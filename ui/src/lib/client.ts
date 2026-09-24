import {
  BookingsController,
  Client,
  Configuration,
  PaymentsController,
  StationsController,
  TripsController,
} from '@wiremock-inc/apimatic-sdkgen-demo';
import { createEffect, createSignal } from 'solid-js';

/** The gateway this UI is shipped against (Spring Boot, plain HTTP on port 8080). */
export const DEFAULT_HOST = 'http://localhost:8080';

const [host, setHost] = createSignal<string | false>(false);

let client;

let stationsController: StationsController;
let tripsController: TripsController;
let bookingsController: BookingsController;
let paymentsController: PaymentsController;

const protocolRegexp = /https|http:\/\//;

createEffect(() => {
  let config: Partial<Configuration> = {
    oAuthClientId: '123213123213',
    oAuthRedirectUri: '/adadfasdf',
    oAuthClientSecret: 'adfasfasf',
    oAuthToken: {
      accessToken: 'thing',
      tokenType: 'thing',
    },
  };

  if (!!host()) {
    config.host = host() as string;
  } else {
    config.host = DEFAULT_HOST;
  }

  client = new Client(config);
  stationsController = new StationsController(client);
  tripsController = new TripsController(client);
  bookingsController = new BookingsController(client);
  paymentsController = new PaymentsController(client);
});

export {
  setHost,
  host,
  stationsController,
  tripsController,
  bookingsController,
  paymentsController,
};
