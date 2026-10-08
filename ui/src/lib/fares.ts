/**
 * The API's bookings carry no price and a trip cannot be fetched by ID, so the price shown
 * in the trips table is carried to the payment page here: first against the trip, then
 * against the booking made for it. sessionStorage survives the redirect to Stripe and back.
 */

const key = (id: string) => `traintravel.fare.${id}`;

export const rememberFare = (id: string, amount: number | undefined) => {
  if (amount !== undefined) sessionStorage.setItem(key(id), String(amount));
};

export const fareFor = (id: string): number | undefined => {
  const stored = sessionStorage.getItem(key(id));
  return stored === null ? undefined : Number(stored);
};
