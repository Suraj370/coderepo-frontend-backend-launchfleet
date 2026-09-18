import * as React from "react"
import { cn } from "cn"
import {
  Slider as SliderPrimitive,
  SliderOutput as SliderOutputPrimitive,
  SliderThumb as SliderThumbPrimitive,
  SliderTrack as SliderTrackPrimitive,
  type SliderProps,
} from "react-aria-components"

function Slider({ className, ...props }: SliderProps) {
  return (
    <SliderPrimitive
      data-slot="slider"
      className={cn("relative flex w-full flex-col gap-1", className)}
      {...props}
    />
  )
}

function SliderTrack({ className, ...props }: React.ComponentProps<typeof SliderTrackPrimitive>) {
  return (
    <SliderTrackPrimitive
      data-slot="slider-track"
      className={cn(
        "relative flex h-5 w-full shrink-0 touch-none items-center",
        className,
      )}
      {...props}
    >
      {(renderProps) => (
        <>
          <div className="h-1.5 w-full rounded-full bg-muted">
            <div
              className="h-full rounded-full bg-primary"
              style={{ width: `${renderProps.state.getThumbPercent(0) * 100}%` }}
            />
          </div>
          <SliderThumbPrimitive
            className="absolute top-1/2 left-(--thumb-percent) size-4 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-primary bg-background shadow transition-colors outline-none data-dragging:cursor-grabbing data-focus-visible:ring-3 data-focus-visible:ring-ring/50 data-disabled:opacity-50"
            index={0}
            style={{
              "--thumb-percent": `${renderProps.state.getThumbPercent(0) * 100}%`,
            } as React.CSSProperties}
          />
        </>
      )}
    </SliderTrackPrimitive>
  )
}

function SliderOutput({ className, ...props }: React.ComponentProps<typeof SliderOutputPrimitive>) {
  return (
    <SliderOutputPrimitive
      data-slot="slider-output"
      className={cn("text-sm tabular-nums text-muted-foreground", className)}
      {...props}
    />
  )
}

export { Slider, SliderTrack, SliderOutput }
