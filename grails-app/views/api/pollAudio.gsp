<%@ page contentType="text/html;charset=UTF-8"%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1" />
<meta name="layout" content="semantic" />
<title>Poll Test</title>
</head>
<body>
<div class="body">
	<h2>Poll Audio</h2>
</div>
<script>
window.onerror = function(message, file, line, col, error) {
	console.log("Error:"+error.message);
	waitPoll();
}
var token='${token}';
function poll() {
	setTimeout(function() {
			console.log("making audio call");
			var a=new Audio("/api/getAudio?token=${token}");
			$('#notifylogo').transition('tada');
			a.play();
			a.addEventListener('ended', function() {
				console.log("finished playing audio");
				poll();
			});
	}, 1);
};
function waitPoll() {
	setTimeout(poll,10000);
}
poll();
</script>
</body>
</html>