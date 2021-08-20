package sttp.tapir.server.netty

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

import io.netty.buffer.{ByteBuf, ByteBufInputStream, Unpooled}
import sttp.capabilities
import sttp.model.HasHeaders
import sttp.tapir.{CodecFormat, RawBodyType, WebSocketBodyOutput}
import sttp.tapir.internal.NoStreams
import sttp.tapir.server.interpreter.ToResponseBody

class NettyToResponseBody extends ToResponseBody[ByteBuf, NoStreams] {
  override val streams: capabilities.Streams[NoStreams] = NoStreams

  // FullHttpMessage - no ctor, hence ByteBuf for content only
  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): ByteBuf = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        Unpooled.copiedBuffer(v.toString, charset)
      case RawBodyType.ByteArrayBody =>
        val bytes = v.asInstanceOf[Array[Byte]]
        Unpooled.copiedBuffer(bytes)
      case RawBodyType.ByteBufferBody =>
        val byteBuffer = v.asInstanceOf[ByteBuffer]
        Unpooled.copiedBuffer(byteBuffer)

      case RawBodyType.InputStreamBody =>
        val stream = v.asInstanceOf[InputStream]

        val buf = Unpooled.buffer()
        //todo
        buf.writeBytes(stream, 6000)
        buf

      case RawBodyType.FileBody         => ???
      case m: RawBodyType.MultipartBody => ???
    }
  }

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): ByteBuf = throw new UnsupportedOperationException

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
  ): ByteBuf = throw new UnsupportedOperationException
}
