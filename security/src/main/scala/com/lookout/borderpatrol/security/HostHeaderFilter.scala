package com.lookout.borderpatrol.security

import com.google.common.net.InternetDomainName
import com.lookout.borderpatrol.util.Combinators.tap
import com.twitter.finagle.util.InetSocketAddressUtil
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.{Status, Response, Request}
import com.twitter.logging.{Level, Logger}
import com.twitter.util.Future
import scala.util.Try

/**
  * Created by rikesh.chouhan on 7/18/16.
  * Check that the request received contains a host entry [if present] that is present in the
  * validHosts set of names specified to this filter. If not then return an empty Future[Response]
  * or error
  *
  * @param validHosts
  */
case class HostHeaderFilter(validHosts: Set[InternetDomainName]) extends SimpleFilter[Request, Response] {

  lazy val validHostStrings = validHosts.map( validHost => validHost.toString )
  private[this] val log = Logger.get(getClass.getPackage.getName)

  /**
    * Attempt to return the hostname by stripping out the port portion including
    * the semicolon from the provided host entry.
    *
    * @param host
    * @return
    */
  private[security] def extractHostName(host: String): String = {
    Try (InetSocketAddressUtil.parseHostPorts(host).head._1).getOrElse(host)
  }

  /**
    * Requests get forwarded to service only for host entries that have been assigned to this filter
    */
  def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    request.host.map( extractHostName(_)) match {
      case None =>
        log.log(Level.WARNING, "Host Header: is empty")
        cannedResponse
      case Some(hostName) if (!validHostStrings(hostName)) =>
        log.log(Level.WARNING, s"Host Header: '${hostName}' not found")
        cannedResponse
      case _ => service(request)
    }
  }

  private[this] def cannedResponse(): Future[Response] = {
    Future.value(tap(Response(Status.BadRequest))(res => {
      res.contentString =
        """<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN""http://www.w3.org/TR/html4/strict.dtd">
          |<HTML><HEAD><TITLE>Bad Request</TITLE>
          |<META HTTP-EQUIV="Content-Type" Content="text/html; charset=us-ascii"></HEAD>
          |<BODY><h2>Bad Request - Invalid Hostname</h2>
          |<hr><p>HTTP Error 400. The request hostname is invalid.</p>
          |</BODY></HTML>
          |* Closing connect""".stripMargin
      res.contentType = "text/html; charset=us-ascii"
    }))
  }
}
