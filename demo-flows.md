# Demo Flows

## Build an environment for this service
* Show the tests failing without
* Ask AI to construct mock APIs from the contracts
* Update the config file to point to these
* Run the tests, adjust the mocks, repeat until passed

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



