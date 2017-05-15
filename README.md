Notify
=========
A voice-based notification server using the AWS Polly API and SQS.

Notify feeds from one or more AWS SQS queues.  A number of Tokens can be established
which have a unique number as well as an SQS queue name associated with them.  They 
may optionally have credentials for access to that queue (and for synthesis of audio
if desired).

A client may retrieve messages from the notifier using the token in a few ways.  It may act as a web
client and receive the next text message (getMessage).  It may be a sessionless device
and get the next text message (getMessageNoSession).  It may also get an audio representation
of the message (getAudio).

Notify manages feeding from the SQS queues such that only one fetch is done from the SQS queue
no matter how many tokens need the next message from that queue.  This supports a number of notification
clients receiving the same message at the same time from the queue.  It also tracks clients by both
token and session -- so you can have multiple browsers on multiple machines feeding from the same
queue.  For devices (see https://github.com/swb1701/NotiPi.git) a unique token can be employed
as no web session is used.  Notify also cleans up any clients or SQS polling threads if clients
go inactive.

Notify is mostly designed for synthesized audio messages to be delivered.  Plain text messages in
the queue will be read directly.  JSON messages in the queue can take forms like:

{"cmd":"speak","text":"the text to speak"} <-- speaks "the text to speak"

{"cmd":"update"} <-- updates the notify client on devices (see https://github.com/swb1701/NotiPi.git)

{"cmd":"speak","text":"speak this in a specific voice","voice":"salli","volume":0.5} <-- speak in a specific voice (see Polly) and at a specific volume

Clients may handle various types of parameters and may not support all the options yet.  Currently,
the Notify server itself only supports the most basic text and simple JSON commands.  NotiPi supports
volume control.  You can also use Prosody within the SSML to change volume, pitch, etc... on all clients.

Notify has a built in javascript audio polling routine.  Its also an easy matter to drop a small amount
of javascript into any web page to give it audio notification capability feeding from a Notify server.  A
sample fragment of an HTML page is included below:

```javascript
...
<input type="checkbox" onchange='audioChange(this);'>Audio Notifications   
...
<script>
var audioOn=1;
function audioChange(cb) {
	if (cb.checked==true) {
		//start audio notifications
		audioOn=1;
		poll();
	} else {
		audioOn=0;
		//stop audio notifications
	}
}
var notifyUrl='${notifyUrl}';
function poll() {
	setTimeout(function() {
		console.log("making audio call");
		var a=new Audio(notifyUrl);
		a.play();
		a.addEventListener('ended',function() {
			console.log("finished playing audio");
			if (audioOn==1)	poll();
		});
	}, 1);
};
</script>  
...
```

In the above code sample ${notifyUrl} would be substituted with the URL of your deployed server in
format like:

https://yourhost.yourdomain.com/api/getAudio?token=yourtoken
