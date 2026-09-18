import { type FormEvent, useEffect, useState } from "react"
import { Link, useNavigate } from "@tanstack/react-router"

import { getApiErrorMessage } from "@/api/errors"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Field, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field"
import { Input } from "@/components/ui/input"
import { useRegister } from "@/features/auth/hooks/useRegister"

export function SignupPage() {
  const navigate = useNavigate()
  const registerAccount = useRegister()
  const [name, setName] = useState("")
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  useEffect(() => {
    if (!registerAccount.error) {
      setErrorMessage(null)
      return
    }

    let cancelled = false

    getApiErrorMessage(registerAccount.error).then((message) => {
      if (!cancelled) {
        setErrorMessage(message)
      }
    })

    return () => {
      cancelled = true
    }
  }, [registerAccount.error])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    registerAccount.mutate(
      { name, email, password },
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
        <CardTitle className="text-[22px] font-bold">Create your account</CardTitle>
        <CardDescription className="text-[#9fb8b2]">
          Start shipping features with confidence.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit}>
          <FieldGroup>
            <Field data-invalid={errorMessage ? true : undefined}>
              <FieldLabel className="text-[#f4f8f6]" htmlFor="name">
                Full name
              </FieldLabel>
              <Input
                id="name"
                type="text"
                placeholder="Sarah Chen"
                value={name}
                onChange={(event) => setName(event.target.value)}
                required
                className="border-[#31514c] bg-[#0a2423] text-[#f4f8f6] placeholder:text-[#6f8b85] focus-visible:border-[#36e79a] focus-visible:ring-[#36e79a]/40"
              />
            </Field>
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
              <FieldLabel className="text-[#f4f8f6]" htmlFor="password">
                Password
              </FieldLabel>
              <Input
                id="password"
                type="password"
                placeholder="••••••••"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
                minLength={8}
                className="border-[#31514c] bg-[#0a2423] text-[#f4f8f6] placeholder:text-[#6f8b85] focus-visible:border-[#36e79a] focus-visible:ring-[#36e79a]/40"
              />
            </Field>
            {errorMessage && <FieldError>{errorMessage}</FieldError>}
            <Button
              type="submit"
              isDisabled={registerAccount.isPending}
              className="bg-[#36e79a] text-[#021916] hover:bg-[#36e79a]/90"
            >
              {registerAccount.isPending ? "Creating account..." : "Create account"}
            </Button>
          </FieldGroup>
        </form>

        <p className="mt-6 text-center text-sm text-[#9fb8b2]">
          Already have an account?{" "}
          <Link className="font-semibold text-[#36e79a] no-underline" to="/login">
            Sign in
          </Link>
        </p>
      </CardContent>
    </Card>
  )
}
