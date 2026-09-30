# Demo Flows

## Build an environment for this service
* Show the tests failing without
* Ask AI to construct mock APIs from the contracts
* Update the config file to point to these
* Run the tests, adjust the mocks, repeat until passed

### Prompt
this service has a set of APIs it depends on that are currently only available as flakey sandboxes. mock each of the APIs in WireMock Cloud and configure the app to use them so
that we're not dependent on the sandboxes any more. ensure that all of the Postman tests pass when running against these mocks.


### Payment failure scenarios
1. Create a test for when the payment gateway returns FRAUD_SUSPECTED.
2. Create a test confirming that the system behaves safely when PayRail times out (>10s).  
3. Create a test for when Bookings rejects the create request. The gateway passes any UUID through as tripRef without checking the trip exists. A real reservation system would likely return 400/422 for
   an unknown trip, or 409 when the train is sold out. Both would become a generic 500 through downstreamStatus, where a clear 4xx would be better.

## Detect a change in a dependency and fix
* Update a contract
* Run the tests with hard validation enabled
* Use AI to interrogate the failure and update the mocks
* Show passing tests

## Record traffic to build a mock for the UI
* Start the runner in record mode
* Start the UI, pointed at the runner proxy port
* Run the tests
* Stop and show the result in the UI
* Run the tests against the resulting mock
* Run the tests against the local runner
 

## Add a test case for a payment failure

## Add a test case for a booking failure

## Examine the API code for error states not covered by tests and create them



