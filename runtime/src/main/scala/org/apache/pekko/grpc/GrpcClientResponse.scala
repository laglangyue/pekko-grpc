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

package org.apache.pekko.grpc

import io.grpc.Status

import java.util.concurrent.CompletionStage
import org.apache.pekko
import org.apache.pekko.dispatch.ExecutionContexts
import org.apache.pekko.grpc.internal.MetadataImpl
import org.apache.pekko.util.FutureConverters.FutureOps
import pekko.annotation.{ApiMayChange, DoNotInherit}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/**
 * Represents the metadata related to a gRPC call with a streaming response
 *
 * Not for user extension
 */
@DoNotInherit
@ApiMayChange
trait GrpcResponseMetadata {

  /**
   * Scala API: The response metadata, the metadata is only for reading and must not be mutated.
   */
  def headers: pekko.grpc.scaladsl.Metadata

  /**
   * Java API: The response metadata, the metadata is only for reading and must not be mutated.
   */
  def getHeaders(): pekko.grpc.javadsl.Metadata

  /**
   * Scala API: Trailers from the server, is completed after the response stream completes
   */
  def trailers: Future[pekko.grpc.scaladsl.Metadata]

  /**
   * Java API: Trailers from the server, is completed after the response stream completes
   */
  def getTrailers(): CompletionStage[pekko.grpc.javadsl.Metadata]
}

/**
 * Represents the metadata related to a gRPC call with a single response value
 *
 * Not for user extension
 */
@DoNotInherit
trait GrpcSingleResponse[T] extends GrpcResponseMetadata {

  /**
   * Scala API: The response body
   */
  def value: T

  /**
   * Java API: The response body
   */
  def getValue(): T
}

class GrpcResponseMetadataImpl(grpcHeaders:io.grpc.Metadata, trailersPromise:Promise[io.grpc.Metadata]) extends GrpcResponseMetadata {

  override def headers: pekko.grpc.scaladsl.Metadata =
    MetadataImpl.scalaMetadataFromGoogleGrpcMetadata(grpcHeaders)
  override def getHeaders(): pekko.grpc.javadsl.Metadata =
    MetadataImpl.javaMetadataFromGoogleGrpcMetadata(grpcHeaders)
  override def trailers: Future[pekko.grpc.scaladsl.Metadata] =
    trailersPromise.future.map(MetadataImpl.scalaMetadataFromGoogleGrpcMetadata)(ExecutionContexts.parasitic)
  override def getTrailers(): CompletionStage[pekko.grpc.javadsl.Metadata] =
    trailersPromise.future.map(MetadataImpl.javaMetadataFromGoogleGrpcMetadata)(ExecutionContexts.parasitic)
      .asJava
}

class GrpcSingleResponseImpl[T](grpcHeaders:io.grpc.Metadata, trailersPromise:Promise[io.grpc.Metadata], messagePromise: Promise[T])
  extends GrpcResponseMetadataImpl(grpcHeaders, trailersPromise) with GrpcSingleResponse[T] {

  var message:T = _

  messagePromise.future.onComplete {
    case Failure(exception) => throw Status.INTERNAL.withDescription("message complete with exception").asRuntimeException()
    case Success(value) => message = value
  }(ExecutionContexts.parasitic)

  override def value: T = message

  override def getValue(): T = message
}
