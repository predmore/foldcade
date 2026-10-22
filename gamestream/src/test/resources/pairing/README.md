Test-only RSA keys and self-signed certificates for the pairing tests: one for
a client, one for a simulated Sunshine host. They protect nothing.

    openssl req -x509 -newkey rsa:2048 -nodes -keyout key.pem -out cert.pem -days 7300 -subj "/CN=..." -sha256
    openssl pkcs8 -topk8 -nocrypt -in key.pem -out key.pk8.pem
