<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1"/>
<meta name="layout" content="semantic"/>
<title>BBDemo</title>
</head>
<body>
<canvas class="float:left;" id="bbcanvas" width="1440"  height="480" style="border:1px solid #000000;">
</canvas>
<script>
function poll() {
	setTimeout(function() {
		$.ajax({url: "/admin/bbtest", success: function(json) {
			var c=document.getElementById("bbcanvas");
			var ctx=c.getContext("2d");
			if (json.blocks.length>0) {
		      ctx.clearRect(0,0,c.width,c.height);
			  ctx.beginPath();
			  var pen=0;
			  for(i=0;i<json.blocks.length;i++) {
				  var block=json.blocks[i];
				  console.log("plotting block #"+json.blocks[i][3]);
				  for(j=0;j<block.length;j=j+2) {
				    if (block[j]==4003) {
					  pen=0;
				    } else if (block[j]==4004 || block[j]==4005) { //show eraser as pen for now
					  pen=1;
				    } else if (block[j]<4000 && block[j+1]<4000) {
					  if (pen==0) {
						ctx.moveTo(block[j]*0.4,(1200-block[j+1])*0.4);
					  } else {
						ctx.lineTo(block[j]*0.4,(1200-block[j+1])*0.4);
					  }			  
				    }
				  }
			  }
			  ctx.stroke();
			};
			poll();
		}});
	}, 1000);
};
poll();
</script>
</body>
</html>