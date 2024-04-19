/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import java.util.concurrent.CompletionStage
import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.grpc.{GrpcSingleResponse, GrpcSingleResponseImpl}
import pekko.util.FutureConverters._
import io.grpc._
import org.apache.pekko.util.OptionVal

import scala.concurrent.{Future, Promise}

/**
 * gRPC Netty based client listener transforming callbacks into a future response
 *
 * INTERNAL API
 */
@InternalApi
private[pekko] final class UnaryCallAdapter[Res] extends ClientCall.Listener[Res] {
  private val responsePromise = Promise[Res]()

  override def onMessage(message: Res): Unit =
    // close over var and make final
    if (!responsePromise.trySuccess(message)) {
      throw Status.INTERNAL.withDescription("More than one value received for unary call").asRuntimeException()
    }

  override def onClose(status: Status, trailers: Metadata): Unit =
    if (status.isOk) {
      if (!responsePromise.isCompleted)
        // No value received so mark the future as an error
        responsePromise.tryFailure(
          Status.INTERNAL.withDescription("No value received for unary call").asRuntimeException(trailers))
    } else {
      responsePromise.tryFailure(status.asRuntimeException(trailers))
    }

  def future: Future[Res] = responsePromise.future
  def cs: CompletionStage[Res] = future.asJava
}

/**
 * gRPC Netty based client listener transforming callbacks into a future response
 *
 * INTERNAL API
 */
// needs to be a separate class because of CompletionStage error handling not bubbling
// exceptions like Scala Futures do ;( flip side is that it saves some garbage
@InternalApi
private[pekko] final class UnaryCallWithMetadataAdapter[Res] extends ClientCall.Listener[Res] {
  private val responsePromise = Promise[GrpcSingleResponse[Res]]()
  private var headers: OptionVal[Metadata] = OptionVal.None
  private val trailerPromise = Promise[Metadata]()

  // always invoked before message
  override def onHeaders(headers: Metadata): Unit =
    this.headers = OptionVal.Some(headers)

  override def onMessage(message: Res): Unit = {
    val headersOnMessage = UnaryCallWithMetadataAdapter.this.headers match {
      case OptionVal.Some(h) => h
      case OptionVal.None    => throw new RuntimeException("Never got headers, this should not happen")
    }
    if (!responsePromise.trySuccess(new GrpcSingleResponseImpl(headersOnMessage, message, trailerPromise))) {
      throw Status.INTERNAL.withDescription("More than one value received for unary call").asRuntimeException()
    }
  }

  override def onClose(status: Status, trailers: Metadata): Unit = {
    trailerPromise.success(trailers)
    if (status.isOk) {
      if (!responsePromise.isCompleted)
        // No value received so mark the future as an error
        responsePromise.tryFailure(
          Status.INTERNAL.withDescription("No value received for unary call").asRuntimeException(trailers))
    } else {
      responsePromise.tryFailure(status.asRuntimeException(trailers))
    }
  }

  def future: Future[GrpcSingleResponse[Res]] = responsePromise.future
  def cs: CompletionStage[GrpcSingleResponse[Res]] = future.asJava
}
