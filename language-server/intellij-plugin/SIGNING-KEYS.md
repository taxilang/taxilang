# Plugin Signing Keys

Location: Stored in GitLab CI/CD variables as base64-encoded values
- `CERTIFICATE_CHAIN` - chain.crt (base64 encoded)
- `PRIVATE_KEY` - private.pem (base64 encoded)
- `PRIVATE_KEY_PASSWORD` - Password for private key

Backup: See Marty

Generated: 21-Nov-25

## Steps to reproduce, if lost:
https://plugins.jetbrains.com/docs/intellij/plugin-signing.html


```bash
openssl genpkey\                                  
  -aes-256-cbc\
  -algorithm RSA\
  -out private_encrypted.pem\
  -pkeyopt rsa_keygen_bits:4096


openssl rsa\                                      
  -in private_encrypted.pem\
  -out private.pem


openssl req\                                      
  -key private.pem\         
  -new\           
  -x509\
  -days 365\
  -out chain.crt
  
## Base64 encode the chain so it can be used in gitlab as a CI/CD variable
cat chain.crt | base64 -w 0 > chain.crt.base64

## Use this value in Gitlab Env Variables
cat chain.crt.base64

## Same for the private key
cat private.pem | base64 -w 0 > private.pem.base64
cat private.pem.base64 
 
```
