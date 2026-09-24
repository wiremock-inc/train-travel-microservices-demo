import {ParentComponent, JSX} from 'solid-js';

const Select: ParentComponent<{
    id?: string;
    onChange?: JSX.EventHandler<HTMLSelectElement, InputEvent>;
}> = (props) => {
    return (
        <select
            class="bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-lg focus:ring-blue-500 focus:border-blue-500 block w-full p-2.5 bg-gray-700 border-gray-600 placeholder-gray-400 text-white focus:ring-blue-500 focus:border-blue-500"
            {...props}
        >
            {props.children}
        </select>
    );
};

export default Select;
