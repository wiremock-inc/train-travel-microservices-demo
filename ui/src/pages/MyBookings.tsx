import Hr from '../components/hr';
import H1 from '../components/typeography/H1';
import {bookingsController} from "../lib/client";
import {createResource, ErrorBoundary, For, Show} from "solid-js";
import Card from "../components/card";
import Spinner from "../components/spinner";
import Button from "../components/forms/button";
import {Booking} from "@wiremock-inc/apimatic-sdkgen-demo";

const bookingsFetcher = async () => {
    const response = await bookingsController.getBookings();
    return response.result.data;
};

const MyBookingsPage = () => {
    const [bookings, {mutate: mutateBookings, refetch: refetchBookings}] = createResource(bookingsFetcher);

    const deleteBooking = async (booking: Booking) => {
        mutateBookings(bookings()?.filter((b) => b.id !== booking.id));

        try {
            await bookingsController.deleteBooking(booking.id!);
        } catch (e) {
            await refetchBookings();
        }
    }

    return (
        <>
            <H1>My bookings</H1>

            <Hr/>

            <Show when={bookings.loading}>
                <Spinner/>
            </Show>
            <Show when={!bookings.loading && !bookings()}>
                <p>You have not made any bookings yet</p>
            </Show>
            <ErrorBoundary fallback={<p>Something went wrong</p>}>
                <For each={bookings()}>
                    {(booking) => (
                        <Card>
                            <dl class="grid grid-cols-2 gap-x-4 mb-6">
                                <dt class="font-bold text-lg">Booking ID</dt>
                                <dd class="mb-2 text-base">{booking.id}</dd>

                                <dt class="font-bold text-lg">Passenger name</dt>
                                <dd class="mb-2 text-base">{booking.passengerName}</dd>

                                <dt class="font-bold text-lg">Has dog</dt>
                                <dd class="mb-2 text-base">{booking.hasDog ? "Yes" : "No"}</dd>

                                <dt class="font-bold text-lg">Has bicycle</dt>
                                <dd class="mb-2 text-base">{booking.hasBicycle ? "Yes" : "No"}</dd>

                            </dl>

                            <div class="flex gap-4">
                                <Button onClick={() => deleteBooking(booking)}>Delete booking</Button>
                            </div>
                        </Card>
                    )}
                </For>
            </ErrorBoundary>
        </>
    );
};

export default MyBookingsPage;
