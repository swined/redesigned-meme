### Build and test

`mvn verify`

Requires JDK11 and maven to build. Depends on jackson, joda.money and junit.   

### Run

`java -jar target/revolut-1.0-SNAPSHOT.jar $PORT`

### API

Every API call returns either HTTP code 200 and a JSON-encoded result, or a relevant 4xx code and a JSON with error description. All endpoints return 400 when invoked with invalid JSON, invalid money amount, or unsupported currency. All endpoints accept and return money/currencies in joda.money format.      

#### `GET /account/{id}`

Returns account balance, for example `{ "balance" : "GBP 17.19" }`, or 404, if account does not exist.

#### `PUT /account/{id}`

Accepts JSON of form `{ "currency" : "{code}" }` and creates an account with requested currency and zero balance. Returns 200 if account was created, or 409 if the account already exists, but the currencies do not match. Returns 200 if an account with matching id and currency already exists. Balance stays untouched in that case. 

#### `PUT /operation/{id}`

Accepts a JSON with account ids as keys and money amounts to add or subtract from them as values. Runs atomically, either all updates are applied, or none. Runs at most once, subsequent invocations do nothing and return the same result as the first one. Returns 200 if the operation succeeded. Returns 404 if any of updated accounts is missing. Returns 409 if an operation with the same id, but different data exists. Returns 412 if any of updated accounts does not have enough funds to perform the operation, or has a different currency than was requested in the update. Long random operation ids, such as UUID, are recommended.

Sample deposit operation: `{ "A" : "GBP 4.20" }`

Sample transfer operation: `{ "A" : "GBP -42", "B" : "GBP 42" }`