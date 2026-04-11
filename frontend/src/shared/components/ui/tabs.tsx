import * as React from "react"

import { cn } from "@/lib/utils"

interface TabsContextValue {
  value: string
  setValue: (value: string) => void
}

const TabsContext = React.createContext<TabsContextValue | null>(null)

function useTabsContext() {
  const context = React.useContext(TabsContext)

  if (!context) {
    throw new Error("Tabs components must be used within <Tabs>")
  }

  return context
}

function Tabs({
  className,
  value: valueProp,
  defaultValue,
  onValueChange,
  ...props
}: React.ComponentProps<"div"> & {
  value?: string
  defaultValue?: string
  onValueChange?: (value: string) => void
}) {
  const [uncontrolledValue, setUncontrolledValue] = React.useState(defaultValue ?? "")
  const value = valueProp ?? uncontrolledValue

  const setValue = React.useCallback(
    (nextValue: string) => {
      if (valueProp === undefined) {
        setUncontrolledValue(nextValue)
      }

      onValueChange?.(nextValue)
    },
    [onValueChange, valueProp]
  )

  return (
    <TabsContext.Provider value={{ value, setValue }}>
      <div data-slot="tabs" className={cn("space-y-4", className)} {...props} />
    </TabsContext.Provider>
  )
}

function TabsList({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      role="tablist"
      data-slot="tabs-list"
      className={cn(
        "bg-muted text-muted-foreground inline-flex w-full flex-wrap items-center gap-1 rounded-none border p-1",
        className
      )}
      {...props}
    />
  )
}

function TabsTrigger({
  className,
  value,
  ...props
}: React.ComponentProps<"button"> & { value: string }) {
  const context = useTabsContext()
  const isActive = context.value === value

  return (
    <button
      type="button"
      role="tab"
      data-slot="tabs-trigger"
      data-state={isActive ? "active" : "inactive"}
      aria-selected={isActive}
      className={cn(
        "text-foreground focus-visible:border-ring focus-visible:ring-ring/50 inline-flex min-h-8 flex-1 items-center justify-center rounded-none px-3 py-1 text-xs font-medium whitespace-nowrap transition-all outline-none focus-visible:ring-1 data-[state=active]:bg-background data-[state=active]:shadow-xs",
        className
      )}
      onClick={() => context.setValue(value)}
      {...props}
    />
  )
}

function TabsContent({
  className,
  value,
  ...props
}: React.ComponentProps<"div"> & { value: string }) {
  const context = useTabsContext()
  const isActive = context.value === value

  return (
    <div
      role="tabpanel"
      data-slot="tabs-content"
      data-state={isActive ? "active" : "inactive"}
      hidden={!isActive}
      className={cn("outline-none", !isActive && "hidden", className)}
      {...props}
    />
  )
}

export { Tabs, TabsContent, TabsList, TabsTrigger }
