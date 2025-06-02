import React from 'react'
import clsx from 'clsx'
import {ExclamationTriangleIcon, LightBulbIcon} from "@heroicons/react/24/outline";
import {Prose} from "@/components/docs/Prose";
import ReactMarkdown from 'react-markdown';

const styles = {
  note: {
    container:
      'rounded-lg p-px bg-gradient-to-b from-sky-400 to-sky-700',
    title: 'text-sky-900 dark:text-sky-400',
    body: 'text-sky-800 [--tw-prose-background:theme(colors.sky.50)] prose-a:text-sky-900 prose-code:text-sky-900 dark:text-slate-300 dark:prose-code:text-slate-300',
  },
  warning: {
    container:
      'rounded-lg p-px bg-gradient-to-b from-amber-400 to-amber-600',
    title: 'text-amber-900 dark:text-amber-500',
    body: 'text-amber-800 [--tw-prose-underline:theme(colors.amber.400)] [--tw-prose-background:theme(colors.amber.50)] prose-a:text-amber-900 prose-code:text-amber-900 dark:text-slate-300 dark:[--tw-prose-underline:theme(colors.sky.700)] dark:prose-code:text-slate-300',
  },
}

const icons = {
  note: (props) => <LightBulbIcon {...props}></LightBulbIcon>,
  warning: (props) => <ExclamationTriangleIcon {...props}></ExclamationTriangleIcon>,
}

type CalloutProps = {
  type: 'info' | 'note' | 'warning',
  title: string,
  children?: React.ReactNode,
  content?: string
}
export function Callout({type , title, children, content}:CalloutProps) {
  let calloutType = type || 'note';

  // We get this wrong so often, just alias info -> note
  if (calloutType === 'info') calloutType = 'note';
  let IconComponent = icons[calloutType]
  const calloutTypeStyles = styles[calloutType]
  if (calloutTypeStyles === undefined) {
    throw new Error('No styles defined for calloutType ' + calloutType)
  }

  // Use content prop if provided, otherwise use children
  // Content prop preserves line breaks for complex markdown (lists, etc.)
  // Children is processed for simple inline markdown
  const markdownContent = content || (children && typeof children === 'string' ? children
    .trim()
    .split('\n')
    .map(line => line.trim())
    .join('\n') : '');

  return (
    <div className={clsx('my-12 flex', styles[calloutType].container)}>
      <div className='bg-slate-800 rounded-lg p-6 w-full h-full'>
        <div className="flex-auto">
          { title ? (<div className='flex mb-4'>
            <IconComponent className={clsx("h-8 w-8 mr-4", styles[calloutType].title)}/>
            <p className={clsx('m-0 font-display text-xl', styles[calloutType].title)}>
              {title}
            </p>

          </div>) : (<></>)}

          <Prose className={clsx('prose', styles[calloutType].body)}>
            <ReactMarkdown>
              {markdownContent}
            </ReactMarkdown>
          </Prose>
        </div>
      </div>
    </div>
  )
}
