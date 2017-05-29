<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1"/>
<meta name="layout" content="main"/>
<title>BTLE Status</title>
</head>
<body>
  <div class="body">
	<table>
	<tr>
	<th>Tag</th><th>Address</th><th>Key</th><th>IP</th><th>RSSI</th><th>Time</th>
	</tr>
	<g:set var="millis" value="${System.currentTimeMillis()}"></g:set>
	<g:each in="${btMap.keySet().sort()}" var="key">
	<g:each in="${btMap[key]}.sort{it.addr}" var="map">
	<tr>
	<td>${nameMap[key]}</td>
	<td>${btMap[key].addr}</td>
	<td>${btMap[key].key}</td>
	<td>${btMap[key].ip}</td>
	<td>${btMap[key].rssi}</td>
	<td>${btMap[key].time-millis}</td>		
	</tr>
	</g:each>
	</g:each>
	</table>  
  </div>
</body>
</html>