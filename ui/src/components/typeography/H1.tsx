import {ParentComponent} from 'solid-js';

const H1: ParentComponent = (props) => {
    return (
        <h1 class="mb-6 text-2xl font-extrabold leading-none tracking-tight text-gray-900 md:text-3xl lg:text-4xl text-white">
            {props.children}
        </h1>
    );
};

export default H1;
