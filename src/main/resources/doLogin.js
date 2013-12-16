// Params
var username = "%s";
var password = "%s";

// Exec
try {
	document.getElementById("username").value = username;
	document.getElementById("password").value = password;
	document.getElementById("login").submit();
} catch (e) {
	alert(e);
}