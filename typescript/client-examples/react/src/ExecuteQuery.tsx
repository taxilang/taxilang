type ExecuteQueryProps = {
  label: string;
} & React.ComponentProps<"button">;

export const ExecuteQuery = ({label, ...props}: ExecuteQueryProps) => {
  return (
    <button {...props}>
      {label}
    </button>
  )
}
