<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1"/>
<meta name="layout" content="semantic"/>
<title>BBDemo</title>
</head>
<body>
<div class="ui buttons" style="margin-bottom:10px;">
	<button id="ext" class="ui button">External</button>
	<div class="or"></div>
	<button id="int" class="ui button">Internal</button>
	<div class="or"></div>
	<button id="proxy" class="ui button">Both</button>
</div>
<div id="expl" style="margin-bottom:10px;"><i>Feed from INTERNAL content</i></div>
<canvas class="float:left;" id="bbcanvas" width="1440"  height="480" style="border:1px solid #000000;">
</canvas>
<script>
var ext=0;
$('#ext').click(function() {
	$('#expl').html("<i>Feed from EXTERNAL content</i>")
	ext=1;	
});
$('#int').click(function() {
	$('#expl').html("<i>Feed from INTERNAL content</i>")
	ext=0;	
});
$('#proxy').click(function() {
	$('#expl').html("<i>Feed from INTERNAL AND EXTERNAL content</i>")
	ext=3
});
function poll() {
	setTimeout(function() {
		$.ajax({url: "/admin/bbtest?ext="+ext, success: function(json) {
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