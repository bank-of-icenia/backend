# Bank of Icenia Backend

## Config

Create a file called `backend.conf` that looks like this:
```hocon
database {
    host = "127.0.0.1"
    port = 5432
    database = "bank"
    username = "<username>"
    password = "<password>"
}

discord {
    client_id = "<CLIENT ID>"
    client_secret = "<CLIENT SECRET>"
}
```
