/**
 * Drives a real Chrome through the whole UI journey:
 *
 *   log in (mock Google IdP) -> find trips -> book -> pay by card (decline + approve)
 *   -> book again -> pay on Stripe's hosted Checkout page (cancel + pay)
 *   -> view my bookings -> delete bookings -> log out
 *
 * Everything behind the UI is the Spring Boot gateway (localhost:8080), which in
 * turn talks only to WireMock Cloud mocks (Stripe's API and hosted page included). Requires:
 *   - the UI dev server on localhost:3000
 *   - the gateway on localhost:8080
 *
 * The browser window is visible by default so the run can be watched; pass
 * --headless to run without a window (e.g. in CI).
 */

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import puppeteer from 'puppeteer-core';

const UI = 'http://localhost:3000';
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const PROFILE_DIR = path.join(os.tmpdir(), `train-travel-e2e-${process.pid}`);
const PASSENGER = `Puppetester ${new Date().toISOString().slice(11, 19)}`;
const STRIPE_PASSENGER = `${PASSENGER} (Stripe)`;

fs.rmSync(PROFILE_DIR, { recursive: true, force: true });

const browser = await puppeteer.launch({
  executablePath: CHROME,
  headless: process.argv.includes('--headless'),
  userDataDir: PROFILE_DIR,
  args: ['--no-first-run', '--no-default-browser-check', '--no-sandbox'],
});

try {
const page = await browser.newPage();
await page.setViewport({ width: 1280, height: 900 });
page.setDefaultTimeout(20000);

const httpProblems = [];
page.on('response', (r) => {
  // the Stripe hosted page has no favicon; the browser's speculative request for one is noise
  if (r.status() >= 400 && !r.url().endsWith('/favicon.ico')) {
    httpProblems.push(`${r.status()} ${r.request().method()} ${r.url()}`);
  }
});
page.on('pageerror', (e) => httpProblems.push(`pageerror: ${e.message}`));

const step = (name) => console.log(`\n--- ${name}`);
const fail = (msg) => {
  console.error(`\nFAILED: ${msg}`);
  if (httpProblems.length) {
    console.error('HTTP >=400 responses seen:');
    httpProblems.forEach((p) => console.error(`  ${p}`));
  }
  process.exitCode = 1;
  throw new Error(msg);
};
const waitFor = (fn, timeout, label, ...args) =>
  page.waitForFunction(fn, { timeout }, ...args).catch(() =>
    fail(`timeout waiting for ${label}`),
  );

/** Clicks the first <button> whose trimmed text matches. */
async function clickButton(text) {
  const clicked = await page.evaluate((t) => {
    const buttons = [...document.querySelectorAll('button')];
    const target = buttons.find((b) => b.textContent.trim() === t);
    if (target) {
      target.click();
      return true;
    }
    return false;
  }, text);
  if (!clicked) fail(`button "${text}" not found`);
}

/** Tomorrow's date, the format the date input wants. */
const tomorrow = new Date(Date.now() + 24 * 3600 * 1000)
  .toISOString()
  .slice(0, 10);

step('open the UI and check logged-out state');
await page.goto(`${UI}/`, { waitUntil: 'domcontentloaded', timeout: 30000 });
await waitFor(
  () => [...document.querySelectorAll('button')].some((b) => b.textContent.trim() === 'Log in'),
  10000,
  'Log in button',
);

step('log in via the mock Google IdP');
await clickButton('Log in');
await waitFor(
  () => location.href.includes('/o/oauth2/v2/auth'),
  10000,
  'IdP authorize page',
);
await page.type('#email', 'puppeteer@example.com');
await page.evaluate(() => {
  document.querySelector('input[type=submit]').click();
});
await waitFor(
  () => location.pathname === '/my-bookings',
  20000,
  'callback to complete and land on my-bookings',
);
await waitFor(
  () => [...document.querySelectorAll('button')].some((b) => b.textContent.trim() === 'Log out'),
  10000,
  'Log out button (logged in)',
);
console.log('logged in, landed on my-bookings');

/**
 * Finds Berlin -> München trips for tomorrow, books the first one and lands on its payment page.
 * Returns the price the trips table showed for it.
 */
async function bookTrip(passenger) {
  await page.goto(`${UI}/booking/new`, { waitUntil: 'domcontentloaded' });
  await waitFor(
    () => document.querySelectorAll('#from option').length > 2,
    15000,
    'stations to load (authenticated /stations call)',
  );
  const pickStation = async (id, label) => {
    const value = await page.$eval(
      id,
      (el, label) =>
        [...el.options].find((o) => o.textContent.trim().includes(label))?.value,
      label,
    );
    if (!value) {
      const options = await page.$eval(id, (el) =>
        [...el.options].map((o) => o.textContent.trim()),
      );
      fail(`station "${label}" not in ${id}; options were: ${JSON.stringify(options)}`);
    }
    return value;
  };
  await page.select('#from', await pickStation('#from', 'Berlin Hauptbahnhof'));
  await page.select('#to', await pickStation('#to', 'München Hauptbahnhof'));
  await page.$eval(
    '#date',
    (el, v) => {
      el.value = v;
      el.dispatchEvent(new Event('change', { bubbles: true }));
    },
    tomorrow,
  );
  await page.waitForSelector('table tbody tr', { timeout: 15000 });
  const tripCount = await page.$$eval('table tbody tr', (rows) => rows.length);
  const price = Number(
    await page.$eval('table tbody tr:first-child td:nth-child(2)', (td) => td.textContent.trim()),
  );
  console.log(`trips found: ${tripCount}, first costs ${price}`);

  await page.click('table tbody tr:first-child button');
  await waitFor(
    () => /^\/booking\/(?!new).+/.test(location.pathname),
    10000,
    'passenger details page',
  );
  await page.waitForSelector('#name');
  await page.type('#name', passenger);
  // the form binds onChange, which never fires from typing alone - dispatch it
  await page.$eval('#name', (el) => el.dispatchEvent(new Event('change', { bubbles: true })));
  await clickButton('Book');
  await waitFor(
    () => /^\/payment\/.+/.test(location.pathname),
    15000,
    'payment page after booking',
  );
  console.log(`booking created, payment page for ${page.url()}`);
  return price;
}

/**
 * On the Stripe hosted Checkout page: checks it is charging the trip's price, clicks an outcome
 * button and waits to be sent back to the UI.
 */
async function chooseOnStripeCheckout(outcome, price) {
  await waitFor(
    () => location.pathname.startsWith('/hosted/checkout/'),
    15000,
    'Stripe hosted Checkout page',
  );
  // the hosted page shows the session amount in minor units
  const expectedAmount = `${Math.round(price * 100)} gbp`;
  const shownAmount = await page.evaluate(
    () =>
      [...document.querySelectorAll('dt')].find((dt) => dt.textContent === 'Amount')
        ?.nextElementSibling?.textContent ?? '(no amount shown)',
  );
  if (!shownAmount.startsWith(expectedAmount)) {
    fail(`Stripe Checkout is charging "${shownAmount}" but the trip costs ${price} (expected "${expectedAmount}")`);
  }
  const button = `[data-mock-outcome=${outcome}]`;
  await page.waitForSelector(button);
  await page.click(button);
  await waitFor(
    (ui) => location.href.startsWith(`${ui}/payment/`),
    15000,
    `Stripe to redirect back to the payment page after "${outcome}"`,
    UI,
  );
}

step('find a trip (Berlin -> München, tomorrow) and book it');
await bookTrip(PASSENGER);

step('pay with the declined card (expect the error panel)');
await page.waitForSelector('#cardNumber');
await page.type('#name', 'J. Doe');
await page.type('#cardNumber', '4000000000000002');
await page.type('#cvc', '123');
await page.type('#expiryMonth', '12');
await page.type('#expiryYear', '2027');
await page.type('#postcode', 'gb');
await clickButton('Pay');
await new Promise((r) => setTimeout(r, 2500));
console.log('PANEL:', await page.evaluate(() => document.querySelector('.bg-red-700')?.textContent ?? '(no panel)'));
await waitFor(
  () => document.body.textContent.includes('Payment was declined'),
  20000,
  'declined payment error to surface in the UI',
);
console.log('decline surfaced in the UI, booking still HELD');

step('pay again with the approved card');
await page.$eval('#cardNumber', (el) => (el.value = ''));
await page.type('#cardNumber', '4242424242424242');
await clickButton('Pay');
await waitFor(
  () => document.body.textContent.includes('Payment complete'),
  20000,
  'payment success panel',
);
console.log(
  'PANEL:',
  await page.evaluate(
    () => document.querySelector('.bg-green-700')?.textContent ?? '(no panel)',
  ),
);
const paymentRef = await page.evaluate(() => {
  const match = document.body.textContent.match(/payment reference is:\s*([0-9a-f-]+)/);
  return match ? match[1] : '(not captured)';
});
console.log(`payment succeeded, reference: ${paymentRef}`);

step('book a second trip to pay for with Stripe');
const stripeTripPrice = await bookTrip(STRIPE_PASSENGER);

step('start Stripe Checkout, then cancel on the hosted page');
await clickButton('Pay with Stripe');
await chooseOnStripeCheckout('cancel', stripeTripPrice);
await waitFor(
  () => document.body.textContent.includes('Stripe payment was cancelled'),
  15000,
  'cancelled Stripe payment notice',
);
console.log('cancel surfaced in the UI, booking still HELD');

step('start Stripe Checkout again and pay on the hosted page');
await clickButton('Pay with Stripe');
await chooseOnStripeCheckout('pay', stripeTripPrice);
await waitFor(
  () => document.body.textContent.includes('Payment complete'),
  20000,
  'payment success panel after Stripe Checkout',
);
const stripeRef = await page.evaluate(() => {
  const match = document.body.textContent.match(/payment reference is:\s*(cs_test_[a-z0-9]+)/);
  return match ? match[1] : null;
});
if (!stripeRef) fail('payment reference is not a Stripe Checkout session id');
console.log(`Stripe payment succeeded, reference: ${stripeRef}`);

step('view my bookings and confirm both bookings are there');
await page.click('a[href="/my-bookings"]');
await waitFor(
  (passengers) =>
    location.pathname === '/my-bookings' &&
    [...document.querySelectorAll('dl')].filter((dl) =>
      passengers.some((p) => dl.textContent.includes(p)),
    ).length === passengers.length,
  15000,
  'both new bookings to appear in my bookings',
  [PASSENGER, STRIPE_PASSENGER],
);
console.log('bookings listed for both passengers');

step('delete the bookings');
// STRIPE_PASSENGER first: its name contains PASSENGER, so match the longer one while both exist
for (const passenger of [STRIPE_PASSENGER, PASSENGER]) {
  await page.evaluate((passenger) => {
    const cards = [...document.querySelectorAll('dl')];
    const card = cards.find((dl) => dl.textContent.includes(passenger));
    card.closest('div').querySelector('button').click();
  }, passenger);
  await waitFor(
    (passenger) => !document.body.textContent.includes(passenger),
    15000,
    `booking for ${passenger} to disappear after delete`,
    passenger,
  );
}
console.log('bookings deleted');

step('log out');
await clickButton('Log out');
await waitFor(
  () => [...document.querySelectorAll('button')].some((b) => b.textContent.trim() === 'Log in'),
  10000,
  'Log in button to return after logout',
);

console.log('\n=== JOURNEY PASSED ===');

if (httpProblems.length) {
  console.log('\nHTTP >=400 responses observed along the way (expected: the declined payment):');
  httpProblems.forEach((p) => console.log(`  ${p}`));
}
} finally {
  await browser.close();
  fs.rmSync(PROFILE_DIR, { recursive: true, force: true });
}