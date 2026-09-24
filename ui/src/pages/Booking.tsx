import { JSX, ParentComponent } from 'solid-js';
import H1 from '../components/typeography/H1';
import { useNavigate, useParams } from '@solidjs/router';
import Row from '../components/forms/row';
import Label from '../components/forms/label';
import Input from '../components/forms/input';
import Button from '../components/forms/button';
import { bookingsController } from '../lib/client';
import { createStore } from 'solid-js/store';
import Card from '../components/card';
import Hr from '../components/hr';

const BookingPage: ParentComponent = () => {
  const params = useParams();
  const navigate = useNavigate();
  const [data, setData] = createStore<{
    tripId: string;
    hasBicycle?: boolean;
    hasDog?: boolean;
    passengerName?: string;
  }>({
    tripId: params.trip_id,
  });

  const setName: JSX.EventHandler<HTMLInputElement, Event> = (e) => {
    setData({ passengerName: e.currentTarget.value });
  };

  const setHasBicycle: JSX.EventHandler<HTMLInputElement, Event> = (e) => {
    setData({ hasBicycle: e.currentTarget.checked });
  };

  const setHasDog: JSX.EventHandler<HTMLInputElement, Event> = (e) => {
    setData({ hasDog: e.currentTarget.checked });
  };

  const submit: JSX.EventHandler<HTMLFormElement, SubmitEvent> = async (e) => {
    e.preventDefault();

    const response = await bookingsController.createBooking(data);

    navigate(`/payment/${response.result.id}`);
  };

  return (
    <div>
      <H1>Passenger details</H1>
      <Hr />
      <Card>
        <form onSubmit={submit}>
          <p>
            <strong>Trip id:</strong> {params.trip_id}
          </p>

          <Row>
            <Label for="name">Passenger name</Label>
            <Input id="name" onChange={setName} />
          </Row>

          <Row>
            <Input type="checkbox" id="hasBicycle" onChange={setHasBicycle} />
            <Label for="hasBicycle" inline={true}>
              Has bicycle
            </Label>
          </Row>

          <Row>
            <Input type="checkbox" id="hasDog" onChange={setHasDog} />
            <Label for="hasDog" inline={true}>
              Has dog
            </Label>
          </Row>

          <Row>
            <Button type="submit">Book</Button>
          </Row>
        </form>
      </Card>
    </div>
  );
};

export default BookingPage;
