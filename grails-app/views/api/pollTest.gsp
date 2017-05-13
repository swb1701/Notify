<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1"/>
<meta name="layout" content="semantic"/>
<title>Poll Test</title>
</head>
<body>
  <div class="body">
  <h2>Poll Test</h2>  
  </div>
<script>
var token='${token}';
function poll() {
	setTimeout(function() {
		$.ajax({url: "/api/getMessage?token=${token}", success: function(result) {
			console.log(result);	
			poll();
		}});
	}, 1);
};
poll();
</script>  
</body>
</html>