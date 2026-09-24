import {ParentComponent} from 'solid-js';

const H3: ParentComponent = (props) => {
    return (
        <h3 class="text-xl font-extrabold text-white mb-6">
            {props.children}
        </h3>
    );
};

export default H3;
