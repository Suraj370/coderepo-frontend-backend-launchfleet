import { type FormEvent, useEffect, useState } from "react"
import { Link, useNavigate } from "@tanstack/react-router"

import { getApiErrorMessage } from "@/api/errors"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { useLogin } from "@/features/auth/hooks/useLogin"

export function LoginPage() {
  const navigate = useNavigate()
  const login = useLogin()
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!login.error) {
      setErrorMessage(null)
      return
    }

    let cancelled = false

    getApiErrorMessage(login.error).then((message) => {
      if (!cancelled) {
        setErrorMessage(message)
      }
    })

    return () => {
      cancelled = true
    }
  }, [login.error])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    login.mutate(
      { email, password },
      {
        onSuccess: () => {
          navigate({ to: "/dashboard" })
        },
      },
    )
  }

  return (
    <Card className="border border-[#21504a] bg-[#092522] text-[#f4f8f6] ring-0">
      <CardHeader>
        <CardTitle className="text-[22px] font-bold">Welcome back</CardTitle>
        <CardDescription className="text-[#9fb8b2]">
          Sign in to your LaunchFleet account.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit}>
          <FieldGroup>
            <Field data-invalid={errorMessage ? true : undefined}>
              <FieldLabel className="text-[#f4f8f6]" htmlFor="email">
                Email
              </FieldLabel>
              <Input
                id="email"
                type="email"
                placeholder="you@company.com"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                required
                className="border-[#31514c] bg-[#0a2423] text-[#f4f8f6] placeholder:text-[#6f8b85] focus-visible:border-[#36e79a] focus-visible:ring-[#36e79a]/40"
              />
            </Field>
            <Field data-invalid={errorMessage ? true : undefined}>
              <div className="flex items-center justify-between">
                <FieldLabel className="text-[#f4f8f6]" htmlFor="password">
                  Password
                </FieldLabel>
                <a
                  className="text-xs text-[#9fb8b2] no-underline hover:text-[#36e79a]"
                  href="#forgot"
                >
                  Forgot password?
                </a>
              </div>
              <Input
                id="password"
                type="password"
                placeholder="••••••••"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
                className="border-[#31514c] bg-[#0a2423] text-[#f4f8f6] placeholder:text-[#6f8b85] focus-visible:border-[#36e79a] focus-visible:ring-[#36e79a]/40"
              />
            </Field>
            {errorMessage && <FieldError>{errorMessage}</FieldError>}
            <Button
              type="submit"
              isDisabled={login.isPending}
              className="bg-[#36e79a] text-[#021916] hover:bg-[#36e79a]/90"
            >
              {login.isPending ? "Signing in..." : "Sign in"}
            </Button>
          </FieldGroup>
        </form>

        <p className="mt-6 text-center text-sm text-[#9fb8b2]">
          Don&apos;t have an account?{" "}
          <Link className="font-semibold text-[#36e79a] no-underline" to="/signup">
            Sign up
          </Link>
        </p>
      </CardContent>
    </Card>
  )
}
