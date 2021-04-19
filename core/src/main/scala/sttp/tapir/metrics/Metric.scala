package sttp.tapir.metrics

import sttp.tapir.Endpoint
import sttp.tapir.model.{ServerRequest, ServerResponse}

case class Metric[M](
    metric: M,
    onRequest: Option[(Endpoint[_, _, _, _], ServerRequest, M) => Unit] = None,
    onResponse: Option[(Endpoint[_, _, _, _], ServerRequest, ServerResponse[_], M) => Unit] = None
) {
  def onRequest(f: (Endpoint[_, _, _, _], ServerRequest, M) => Unit): Metric[M] = copy(onRequest = Some(f))
  def onResponse(f: (Endpoint[_, _, _, _], ServerRequest, ServerResponse[_], M) => Unit): Metric[M] = copy(onResponse = Some(f))
}
