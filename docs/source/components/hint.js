import { MDXProvider } from '@mdx-js/react';

export function Prefer({props}) {
    return (
        <div>
            <MDXProvider>```taxi
                {props.children}
                ```
            </MDXProvider>
        </div>
    )
}
export function Discourage({props}) {
    return (
        <div>
            <MDXProvider>```taxi
                {props.children}
                ```
            </MDXProvider>
        </div>
    )
}
export function Hint({type, ...props}) {
    return (
        <MDXProvider>{props.children}</MDXProvider>
    )
}