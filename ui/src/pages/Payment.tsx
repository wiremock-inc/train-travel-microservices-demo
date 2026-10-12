import { createStore } from 'solid-js/store';
import Button from '../components/forms/button';
import Input from '../components/forms/input';
import Label from '../components/forms/label';
import Row from '../components/forms/row';
import Hr from '../components/hr';
import H2 from '../components/typeography/H2';
import { JSX, Show, createResource, createSignal, onMount } from 'solid-js';
import {
  bookingsController,
  gatewayPost,
  paymentsController,
} from '../lib/client';
import { A, useParams, useSearchParams } from '@solidjs/router';
import { CurrencyEnum } from '@wiremock-inc/apimatic-sdkgen-demo';
import { Card } from '@wiremock-inc/apimatic-sdkgen-demo';
import { ObjectEnum } from '@wiremock-inc/apimatic-sdkgen-demo';
import { default as CardComponent } from '../components/card';
import H1 from '../components/typeography/H1';
import Spinner from '../components/spinner';
import { fareFor } from '../lib/fares';

const bookingFetcher = async (id: string) => {
  const response = await bookingsController.getBooking(id);
  return response.result;
};

const Payment = () => {
  const params = useParams();
  const [form, setForm] = createStore<Card>({} as Card);
  const [isPosting, setIsPosting] = createSignal(false);
  const [searchParams] = useSearchParams();
  const [paymentResult, setPaymentResult] = createSignal<
    { id?: string } | undefined
  >();
  const [paymentError, setPaymentError] = createSignal<Error | undefined>();
  const bookingId = () => params.booking_id;
  const [booking] = createResource(bookingId, bookingFetcher);
  const price = () => fareFor(params.booking_id);
  const canPay = () =>
    price() !== undefined && !isPosting() && paymentResult() === undefined;
  // The API's trips have a price but no currency
  const fare = () => ({ currency: CurrencyEnum.Gbp, amount: price()! });

  /** Prefer the gateway's RFC 7807 problem detail over the SDK's generic message. */
  const paymentErrorDetail = () => {
    const error = paymentError() as
      | (Error & { result?: { detail?: string }; body?: unknown })
      | undefined;
    if (error?.result?.detail) return error.result.detail;
    const raw = error?.body;
    try {
      const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
      if (parsed && typeof parsed === 'object' && 'detail' in parsed) {
        return String(parsed.detail);
      }
    } catch {
      /* not JSON - fall through to the generic message */
    }
    return error?.message;
  };

  /** Hands the browser over to Stripe's hosted Checkout page. */
  const payWithStripe = async () => {
    setIsPosting(true);
    try {
      const session = await gatewayPost<{ url: string }>(
        `/bookings/${params.booking_id}/payment/checkout`,
        fare()
      );
      window.location.href = session.url;
    } catch (e) {
      setPaymentError(e as Error);
      setIsPosting(false);
    }
  };

  /** Back from Stripe: the gateway verifies the session and confirms the booking. */
  onMount(async () => {
    if (searchParams.stripe_cancelled) {
      setPaymentError(
        new Error('Stripe payment was cancelled. Your booking has not been paid.')
      );
      return;
    }
    const sessionId = searchParams.stripe_session_id;
    if (!sessionId) return;
    setIsPosting(true);
    try {
      setPaymentResult(
        await gatewayPost<{ id: string }>(
          `/bookings/${params.booking_id}/payment/checkout/${sessionId}/complete`
        )
      );
    } catch (e) {
      setPaymentError(e as Error);
    }
    setIsPosting(false);
  });

  const submit: JSX.EventHandler<HTMLFormElement, Event> = async (e) => {
    e.preventDefault();
    setIsPosting(true);

    try {
      const response = await paymentsController.createBookingPayment(
        params.booking_id,
        {
          ...fare(),
          source: {
            // the API's oneOf on source requires the object discriminator
            object: ObjectEnum.Card,
            name: form.name,
            number: form.number,
            cvc: form.cvc,
            expMonth: form.expMonth,
            expYear: form.expYear,
            addressCountry: form.addressCountry,
          },
        }
      );
      setPaymentResult(response.result);
      setIsPosting(false);
      setPaymentError(undefined);
    } catch (e) {
      setPaymentError(e as Error);
      setIsPosting(false);
    }
  };

  return (
    <>
      <H1>Payment</H1>

      <Hr />

      <div class="flex">
        <div class="min-w-80">
          <CardComponent>
            <Show when={booking.loading}>
              <Spinner />
            </Show>

            <Show when={booking()}>
              <H2>Your booking</H2>
              <p class="text-xl mb-4 font-bold">{booking()?.passengerName}</p>
              <dl>
                <dt class="font-bold text-lg">Has dog</dt>
                <dd class="mb-2 text-base">
                  {booking()?.hasDog ? 'YES' : 'NO'}
                </dd>

                <dt class="font-bold text-lg">Has bicycle</dt>
                <dd class="mb-2 text-base">
                  {booking()?.hasBicycle ? 'YES' : 'NO'}
                </dd>

                <dt class="font-bold text-lg">Trip ID</dt>
                <dd class="mb-2 text-base">{booking()?.tripId}</dd>

                <dt class="font-bold text-lg">Price</dt>
                <dd class="mb-2 text-base">{price() ?? 'Unknown'}</dd>
              </dl>
            </Show>
          </CardComponent>
        </div>
        <div class="lg:w-7/12 mx-auto">
          <CardComponent>
            <form onSubmit={submit}>
              <H2>Payment details</H2>

              <Show when={paymentError()}>
                <div class="mb-6 text-wrap break-words rounded p-4 bg-red-700">
                  <h3 class="mb-4 text-2xl font-bold">Error!</h3>
                  <p class="text-lg">{paymentErrorDetail()}</p>
                </div>
              </Show>

              <Show when={paymentResult()}>
                <div class="mb-6 text-wrap break-words rounded p-4 bg-green-700">
                  <h3 class="mb-4 text-2xl font-bold">Payment complete</h3>
                  <p class="text-lg">
                    Your payment reference is:{' '}
                    <strong>{paymentResult()?.id}</strong>
                  </p>
                  <p>
                    <A
                      href="/my-bookings"
                      class="text-lg inactive font-bold block my-4"
                    >
                      View my bookings
                    </A>
                  </p>
                </div>
              </Show>

              <Row>
                <Button
                  onClick={payWithStripe}
                  disabled={!canPay()}
                >
                  Pay with Stripe
                </Button>
              </Row>

              <Hr />

              <p class="mb-4 text-lg">Or pay by card</p>

              <Row>
                <Label for="name">Name on card</Label>
                <Input
                  id="name"
                  onInput={(e) => setForm({ name: e.currentTarget.value })}
                />
              </Row>
              <Row class="mb-4 py-2 flex gap-2.5">
                <div class="flex-1">
                  <Label for="cardNumber">Card number</Label>
                  <Input
                    id="cardNumber"
                    onInput={(e) => setForm({ number: e.currentTarget.value })}
                  />
                </div>
                <div class="flex-2">
                  <Label for="cvc">CVC number</Label>
                  <Input
                    id="cvc"
                    onInput={(e) =>
                      setForm({ cvc: parseInt(e.currentTarget.value, 10) })
                    }
                  />
                </div>
              </Row>
              <Row class="mb-4 py-2 flex gap-2.5">
                <div class="flex-auto">
                  <Label for="expiryMonth">Expiry month</Label>
                  <Input
                    id="expiryMonth"
                    onInput={(e) =>
                      setForm({ expMonth: BigInt(e.currentTarget.value) })
                    }
                  />
                </div>

                <div class="flex-auto">
                  <Label for="expiryYear">Expiry year</Label>
                  <Input
                    id="expiryYear"
                    onInput={(e) =>
                      setForm({ expYear: BigInt(e.currentTarget.value) })
                    }
                  />
                </div>
              </Row>

              <Hr />

              <Row>
                <Label for="country">Country</Label>
                <Input
                  id="postcode"
                  onInput={(e) =>
                    setForm({ addressCountry: e.currentTarget.value })
                  }
                />
              </Row>

              <Button
                type="submit"
                disabled={!canPay()}
              >
                Pay
              </Button>
            </form>
          </CardComponent>
        </div>
      </div>
    </>
  );
};

export default Payment;
