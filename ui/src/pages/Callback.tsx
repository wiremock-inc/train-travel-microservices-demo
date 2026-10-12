import { Component, createSignal, onMount, Show } from 'solid-js';
import { useNavigate, useSearchParams } from '@solidjs/router';
import { exchangeCodeForToken, saveToken, verifyState } from '../lib/auth';
import { setIdToken } from '../lib/client';
import H2 from '../components/typeography/H2';
import Button from '../components/forms/button';
import Spinner from '../components/spinner';

/** OAuth redirect target: exchanges the authorization code and signs the user in. */
const CallbackPage: Component = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = createSignal<string | false>(false);

  onMount(async () => {
    const code = searchParams.code;
    if (!code) {
      setError('No authorization code was returned by the login page.');
      return;
    }
    if (!verifyState(searchParams.state)) {
      setError('The OAuth state did not match this browser session.');
      return;
    }
    try {
      const token = await exchangeCodeForToken(code);
      saveToken(token);
      setIdToken(token);
      navigate('/my-bookings', { replace: true });
    } catch (e) {
      setError(`Login failed: ${(e as Error).message}`);
    }
  });

  return (
    <div class="text-left max-w-2xl mx-auto">
      <H2>{error() ? 'Login failed' : 'Signing you in…'}</H2>
      <Show when={error()} fallback={<Spinner />}>
        <p class="mb-4 text-red-400">{error()}</p>
        <Button onClick={() => navigate('/', { replace: true })}>
          Back to home
        </Button>
      </Show>
    </div>
  );
};

export default CallbackPage;