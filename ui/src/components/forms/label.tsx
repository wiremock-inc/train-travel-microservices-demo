import {ParentComponent} from 'solid-js';

const Label: ParentComponent<{ for?: string; inline?: boolean }> = (props) => {
    return (
        <label
            classList={{block: !props.inline, 'ml-2': props.inline}}
            class="text-left mb-2 text-sm font-medium text-gray-900 text-white"
            {...props}
        >
            {props.children}
        </label>
    );
};

export default Label;
