# Bank of Icenia Backend

## Config

Create a file called `backend.conf` that looks like this:
```hocon
# The site's port
port = 8080

database {
    host = "127.0.0.1"
    port = 5432 # PostgreSQL's default port
    database = "bank"
    username = "<username>"
    password = "<password>"
}

discord {
    client_id = "<CLIENT ID>"
    client_secret = "<CLIENT SECRET>"
    # Where users should be redirected to from Discord
    post_auth_dest = "/callback"
    # Webhook for deposit / withdrawal logs
    webhook = "<WEBHOOK URL>"
}
```
