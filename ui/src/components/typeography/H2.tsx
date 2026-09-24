import {ParentComponent} from 'solid-js';

const H2: ParentComponent = (props) => {
    return (
        <h1 class="mb-6 text-2xl font-extrabold text-white">
            {props.children}
        </h1>
    );
};

export default H2;
