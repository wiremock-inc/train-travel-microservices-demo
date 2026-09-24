import { ParentComponent } from 'solid-js';

const Row: ParentComponent<{ class?: string }> = (props) => {
  return (
    <div class="mb-4 py-2" {...props}>
      {props.children}
    </div>
  );
};

export default Row;
