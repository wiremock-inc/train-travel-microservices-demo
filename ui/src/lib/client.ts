import {
  BookingsController,
  Client,
  Configuration,
  PaymentsController,
  StationsController,
  TripsController,
} from '@wiremock-inc/apimatic-sdkgen-demo';
import { createEffect, createSignal } from 'solid-js';
import { clearToken, loadToken } from './auth';

/** The gateway this UI is shipped against (Spring Boot, plain HTTP on port 8080). */
export const DEFAULT_HOST = 'http://localhost:8080';

const [host, setHost] = createSignal<string | false>(false);

/** The OAuth access token, seeded from localStorage so sessions survive refreshes. */
const [accessToken, setAccessToken] = createSignal<string | false>(
  loadToken() || false
);

let client;

let stationsController: StationsController;
let tripsController: TripsController;
let bookingsController: BookingsController;
let paymentsController: PaymentsController;

createEffect(() => {
  const config: Partial<Configuration> = {
    host: (host() as string) || DEFAULT_HOST,
  };

  const token = accessToken();
  if (token) {
    config.oAuthToken = { accessToken: token, tokenType: 'Bearer' };
  }

  client = new Client(config);
  stationsController = new StationsController(client);
  tripsController = new TripsController(client);
  bookingsController = new BookingsController(client);
  paymentsController = new PaymentsController(client);
});

/**
 * Calls a gateway endpoint the generated SDK has no operation for (the Stripe Checkout
 * endpoints), with the same host and bearer token the SDK controllers use.
 */
export const gatewayPost = async <T>(path: string, body?: unknown): Promise<T> => {
  const override = host() as string;
  const base = !override
    ? DEFAULT_HOST
    : /^https?:\/\//.test(override)
      ? override
      : `https://${override}`;
  const response = await fetch(`${base}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken() ? { Authorization: `Bearer ${accessToken()}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const json = await response.json().catch(() => undefined);
  if (!response.ok) {
    throw new Error(json?.detail ?? `The gateway returned ${response.status}`);
  }
  return json as T;
};

/** Drops the stored token and rebuilds the controllers unauthenticated. */
export const logout = () => {
  clearToken();
  setAccessToken(false);
};

export {
  setHost,
  host,
  accessToken,
  setAccessToken,
  stationsController,
  tripsController,
  bookingsController,
  paymentsController,
};