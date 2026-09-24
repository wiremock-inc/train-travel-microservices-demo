import { createSignal, JSX, ParentComponent, Show } from 'solid-js';

import styles from './App.module.css';
import { A } from '@solidjs/router';
import Input from './components/forms/input';
import { setHost, host } from './lib/client';
import Button from './components/forms/button';

const PROTOCOL_REGEXP = /https:\/\//g;

const App: ParentComponent = (props) => {
  const [overrideHost, setOverrideHost] = createSignal<string | false>(false);
  const navClasses =
    'gap-x-5 text-blue-400 text-base font-bold hover:text-gray-300';

  const updateOverrideHost: JSX.EventHandler<HTMLInputElement, Event> = (e) => {
    let value = e.currentTarget.value;
    setOverrideHost(value);
  };

  const changeApiHost = () => {
    if (PROTOCOL_REGEXP.test(overrideHost() as string)) {
      setOverrideHost((overrideHost() as string).replace(PROTOCOL_REGEXP, ''));
    }

    setHost(overrideHost());
  };

  const resetHost = () => {
    setHost(false);
    setOverrideHost(false);
  };

  return (
    <div class={styles.App}>
      <nav class="border-gray-400 border-b-2 mb-10">
        <div class="max-w-screen-xl flex flex-wrap items-center mx-auto p-4 gap-20">
          <div class="flex gap-x-5">
            <a href="https://wiremock.io/">
              <img
                src="https://cdn.prod.website-files.com/63496a104e321d427fde91a9/668516ff2492ff79aa390de3_wiremock-cloud-logo-1200px.svg"
                alt="WireMock Cloud"
                class="max-w-40"
              />
            </a>
            <A href="/" class="text-gray-50 text-xl font-bold">
              Train travel demo
            </A>
          </div>

          <div id="navbar-default" class="flex gap-3">
            <A
              href="/booking/new"
              class={navClasses}
              activeClass="underline text-gray-300"
            >
              Book a trip
            </A>
            <A
              href="/my-bookings"
              class={navClasses}
              activeClass="underline text-gray-300"
            >
              My bookings
            </A>
          </div>

          <div class="flex gap-3 items-center flex-1">
            <Input
              id="apiURI"
              class="inline w-full"
              onChange={updateOverrideHost}
              placeholder="Override API host"
              value={overrideHost() ? (overrideHost() as string) : ''}
            />
            <Show when={!host()}>
              <Button
                onClick={() => changeApiHost()}
                disabled={!overrideHost()}
              >
                Change
              </Button>
            </Show>
            <Show when={host()}>
              <Button onClick={() => resetHost()}>Reset</Button>
            </Show>
          </div>
        </div>
      </nav>

      <main class="text-white max-w-screen-xl mx-auto mb-10 px-4 text-left">
        {props.children}
      </main>

      <footer class="rounded-lg shadow m-4 bg-gray-800 max-w-screen-xl mx-auto">
        <div class="w-full mx-auto max-w-screen-xl p-4 md:flex md:items-center md:justify-between text-sm text-gray-500 sm:text-center text-gray-400">
          <div>
            © 2024{' '}
            <a href="https://wiremock.io/" class="hover:underline">
              Developed and powered by WireMock
            </a>
          </div>
          <div>
            Built by WireMock, based on the Train Travel API spec by{' '}
            <a href="https://bump.sh/">Bump.sh</a>
          </div>
        </div>
      </footer>
    </div>
  );
};

export default App;
