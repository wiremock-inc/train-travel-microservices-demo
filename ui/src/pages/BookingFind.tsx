import { Component, createResource, For, JSX, Match, Switch } from 'solid-js';
import Select from '../components/forms/select';
import Label from '../components/forms/label';
import Row from '../components/forms/row';
import { useNavigate, useSearchParams } from '@solidjs/router';
import Hr from '../components/hr';
import H2 from '../components/typeography/H2';
import { stationsController, tripsController } from '../lib/client';
import Cart from '../components/icons/cart';
import Spinner from '../components/spinner';
import Button from '../components/forms/button';
import { Trip } from '@wiremock-inc/apimatic-sdkgen-demo';

const fetchStations = async () => {
  const response = await stationsController.getStations();
  return response.result.data;
};

const fetchTrips = async (state: {
  from?: string;
  to?: string;
  date?: string;
}) => {
  if (state.from && state.to && state.date) {
    try {
      const response = await tripsController.getTrips(
        state.from,
        state.to,
        state.date
      );
      return response.result.data;
    } catch (e) {
      throw e;
    }
  }
};

const BookingFindPage: Component = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const derivedState = () => {
    return {
      from: searchParams.from,
      to: searchParams.to,
      date: searchParams.date,
    };
  };
  const [stations] = createResource(fetchStations);
  const [trips] = createResource(derivedState, fetchTrips);
  const navigate = useNavigate();
  const selectTrip = (trip: Trip) => {
    navigate(`/booking/${trip.id}`);
  };

  const changeFrom: JSX.EventHandler<HTMLSelectElement, InputEvent> = (e) =>
    setSearchParams({ from: e.currentTarget.value });
  const changeTo: JSX.EventHandler<HTMLSelectElement, InputEvent> = (e) =>
    setSearchParams({ to: e.currentTarget.value });
  const changeDate: JSX.EventHandler<HTMLInputElement, Event> = (e) =>
    setSearchParams({ date: e.currentTarget.value });

  const tdClasses =
    'py-2 px-4 text-m group-hover:bg-gray-700 transition-all group-last:last:rounded-br-md group-last:first:rounded-bl-md';
  return (
    <div class="text-left">
      <form>
        <H2>Find trip</H2>

        <div class="grid sm:grid-cols-1 sm:grid-rows-3 md:grid-cols-3 md:grid-rows-1 gap-x-2.5">
          <Row>
            <Label for="from">Departing from</Label>
            <Select id="from" onChange={changeFrom}>
              <option>Select</option>
              <For each={stations()}>
                {(station) => (
                  <option
                    value={station.id}
                    selected={station.id === searchParams.from}
                  >
                    {station.name}
                  </option>
                )}
              </For>
            </Select>
          </Row>
          <Row>
            <Label for="to">Going to</Label>
            <Select id="to" onChange={changeTo}>
              <option>Select</option>
              <For each={stations()}>
                {(station) => (
                  <option
                    value={station.id}
                    selected={station.id === searchParams.to}
                  >
                    {station.name}
                  </option>
                )}
              </For>
            </Select>
          </Row>
          <Row>
            <Label for="date">Depature date</Label>
            <input
              onChange={changeDate}
              id="date"
              type="date"
              value={searchParams.date}
              class="border leading-none text-sm rounded-lg block w-full p-2.5 bg-gray-700 border-gray-600 placeholder-gray-400 text-white focus:ring-blue-500 focus:border-blue-500"
            />
          </Row>
        </div>
      </form>

      <Hr />

      <div>
        <Switch
          fallback={
            <p class="mx-auto max-w-m text-center">
              Select departing station, destination station and a departure date
              to see a list of trips.
            </p>
          }
        >
          <Match when={trips.loading}>
            <Spinner />
          </Match>
          <Match when={trips.error}>
            <div class="bg-red-900 rounded-md max-w-xl text-center p-4 mx-auto">
              <p>{trips.error.result.detail}</p>
            </div>
          </Match>
          <Match when={trips()?.length}>
            <H2>Trips</H2>
            <table
              class="w-fulltext-left w-full
                    rtl:text-right rounded-md"
            >
              <thead class="text-base text-gray-400">
                <tr>
                  <th class="py-2 px-4 text-gray-100 text-l bg-gray-600 rounded-tl-md">
                    Operator
                  </th>
                  <th class="py-2 px-4 text-gray-100 text-l bg-gray-600">
                    Price
                  </th>
                  <th class="py-2 px-4 text-gray-100 text-l bg-gray-600">
                    Dogs allowed
                  </th>
                  <th class="py-2 px-4 text-gray-100 text-l bg-gray-600">
                    Bicycles allowed
                  </th>
                  <th class="py-2 px-4 text-gray-100 text-l bg-gray-600">
                    Arrival time
                  </th>
                  <th class="py-2 px-4 text-gray-100 text-l bg-gray-600">
                    Depature time
                  </th>
                  <th class="py-2 px-4 text-gray-100 text-l bg-gray-600 rounded-tr-md">
                    Book
                  </th>
                </tr>
              </thead>
              <tbody class="text-base">
                <For each={trips()}>
                  {(trip) => (
                    <tr class="border-b bg-gray-800 border-gray-800 hover:text-gray-200 group">
                      <td class={tdClasses}>{trip.operator}</td>
                      <td class={tdClasses}>{trip.price}</td>
                      <td class={tdClasses}>
                        <Switch>
                          <Match when={trip.dogsAllowed === true}>Yes</Match>
                          <Match when={trip.dogsAllowed === false}>No</Match>
                        </Switch>
                      </td>
                      <td class={tdClasses}>
                        <Switch>
                          <Match when={trip.bicyclesAllowed === true}>
                            Yes
                          </Match>
                          <Match when={trip.bicyclesAllowed === false}>
                            No
                          </Match>
                        </Switch>
                      </td>
                      <td class={tdClasses}>{trip.arrivalTime}</td>
                      <td class={tdClasses}>{trip.departureTime}</td>
                      <td class={tdClasses}>
                        <Button onClick={() => selectTrip(trip)}>
                          <Cart /> Book
                        </Button>
                      </td>
                    </tr>
                  )}
                </For>
              </tbody>
            </table>
          </Match>
        </Switch>
      </div>
    </div>
  );
};

export default BookingFindPage;
