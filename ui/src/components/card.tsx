import { ParentComponent } from 'solid-js';

const Card: ParentComponent = (props) => (
  <div
    class="p-6 border rounded-lg shadow bg-gray-800 border-gray-700 mb-10"
    {...props}
  >
    {props.children}
  </div>
);

export default Card;
