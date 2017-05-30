<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1"/>
<meta name="layout" content="semantic"/>
<title>BTLE Status</title>
</head>
<body>
  <div class="body">
	<table class="ui compact striped table">
	<tr>
	<th>Tag</th><th>Address</th><th>Key</th><th>IP</th><th>RSSI</th><th>Time</th>
	</tr>
	<g:set var="millis" value="${System.currentTimeMillis()}"></g:set>
	<g:each in="${btlist.sort{-1*it.time}}" var="map" status="j">
	<tr>
	<td>${nameMap[map.addr]}</td>
	<td>${map.addr}</td>
	<td>${nameMap[map.key]} (${map.key.substring(5)})</td>
	<td>${nameMap[map.ip]} (${map.ip})</td>
	<td>${map.rssi}</td>
	<td>${millis-map.time}</td>		
	</tr>
	</g:each>
	</table>  
  </div>
</body>
</html>