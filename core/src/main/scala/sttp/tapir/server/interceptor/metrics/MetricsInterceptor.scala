package sttp.tapir.server.interceptor.metrics

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.metrics.Metric
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.interpreter.BodyListenerSyntax._
import sttp.tapir.{DecodeResult, Endpoint}

class MetricsInterceptor[F[_], B](metrics: List[Metric[_]], ignoreEndpoints: Seq[Endpoint[_, _, _, _]]) extends EndpointInterceptor[F, B] {

  override def apply(responder: Responder[F, B], endpointHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[I](
          ctx: DecodeSuccessContext[F, I]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] =
        if (ignoreEndpoints.contains(ctx.endpoint)) endpointHandler.onDecodeSuccess(ctx)
        else {
          for {
            _ <- collectMetrics { case Metric(m, Some(onRequest), _) => onRequest(ctx.endpoint, ctx.request, m) }
            response <- endpointHandler.onDecodeSuccess(ctx)
          } yield withBodyOnComplete(response) {
            collectMetrics { case Metric(m, _, Some(onResponse)) => onResponse(ctx.endpoint, ctx.request, response, m) }
          }
        }

      private def withBodyOnComplete(
          sr: ServerResponse[B]
      )(cb: => F[Unit])(implicit bodyListener: BodyListener[F, B]): ServerResponse[B] =
        sr match {
          case sr @ ServerResponse(_, _, Some(body)) => sr.copy(body = Some(body.onComplete { cb }))
          case sr @ ServerResponse(_, _, None) =>
            cb
            sr
        }

      /** If there's some `ServerResponse` collects `onResponse` as well as `onRequest` metric which was not collected in `onDecodeSuccess` stage.
        */
      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] =
        if (ignoreEndpoints.contains(ctx.endpoint)) endpointHandler.onDecodeFailure(ctx)
        else {
          ctx.failure match {
            case _: DecodeResult.Mismatch => endpointHandler.onDecodeFailure(ctx)
            case _ =>
              for {
                response <- endpointHandler.onDecodeFailure(ctx)
                _ <-
                  if (response.isDefined) collectMetrics { case Metric(m, Some(onRequest), _) => onRequest(ctx.endpoint, ctx.request, m) }
                  else monad.unit(())
              } yield response.map(sr =>
                withBodyOnComplete(sr) {
                  collectMetrics { case Metric(m, _, Some(onResponse)) => onResponse(ctx.endpoint, ctx.request, sr, m) }
                }
              )
          }
        }
    }

  private def collectMetrics(pf: PartialFunction[Metric[_], Unit])(implicit monad: MonadError[F]): F[Unit] = {
    def collect(metrics: List[Metric[_]]): F[Unit] = {
      metrics match {
        case Nil                            => ().unit
        case m :: tail if pf.isDefinedAt(m) => monad.eval(pf(m)).flatMap(_ => collect(tail))
        case _ :: tail                      => collect(tail)
      }
    }
    collect(metrics)
  }

}
