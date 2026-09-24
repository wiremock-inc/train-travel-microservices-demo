import { Component, createSignal } from 'solid-js';
import { A } from '@solidjs/router';
import H2 from '../components/typeography/H2';
import Row from '../components/forms/row';
import Input from '../components/forms/input';
import H3 from '../components/typeography/H3';
import Hr from '../components/hr';

const HomePage: Component = () => {
  const [, setApiOverride] = createSignal();

  return (
    <div class="text-left max-w-2xl mx-auto">
      <H2>Welcome to the WireMock train travel demo app</H2>
      <p class="mb-4">
        This application was developed by WireMock and is based on the Train
        travel{' '}
        <a
          href="https://bump.sh/bump-examples/doc/train-travel-api"
          class="text-blue-400 hover:text-gray-300 hover:underline"
        >
          OpenAPI spec produced by Bump.sh
        </a>
        .
      </p>

      <p class="mb-4">
        You can get started by{' '}
        <A
          href="/booking/new"
          class="text-blue-400 hover:text-gray-300 hover:underline"
        >
          searching for available train trips and
        </A>{' '}
        then making a booking.
      </p>

      <Hr />

      <H3>Use your own API</H3>

      <p class={'mb-4'}>
        If you are running your own implementation or mock of the Train travel
        demo then you can point this application at that api by entering it
        below
      </p>

      <Row>
        <label for="apiOverride" class={'mb-2 block'}>
          API URI:
        </label>
        <Input
          id="apiOverride"
          onChange={setApiOverride}
          placeholder="https://my-api-host.com/api"
        />
      </Row>
    </div>
  );
};

export default HomePage;
