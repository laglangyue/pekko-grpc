/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.grpc.internal

import org.apache.pekko.util.ByteString

abstract class Codec {
  val name: String

  def compress(bytes: ByteString): ByteString
  def uncompress(bytes: ByteString): ByteString

  /**
   * Process the given frame bytes, uncompress if the compression bit is set. Identity
   * codec will fail with a [[io.grpc.StatusException]] if the compressedBit is set.
   */
  def uncompress(compressedBitSet: Boolean, bytes: ByteString): ByteString

  def isCompressed: Boolean = this != Identity
}