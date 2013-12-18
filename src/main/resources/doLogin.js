// Params
var username = "%s";
var password = "%s";

// Exec
Sizzle("#username")[0].value = username;
Sizzle("#password")[0].value = password;

Sizzle("#login")[0].onsubmit = "";
Sizzle("#login")[0].submit();
