import {
  ArrowRight,
  BarChart3,
  Check,
  ChevronDown,
  Folder,
  FlaskConical,
  GitBranch,
  Heart,
  LayoutDashboard,
  Network,
  Play,
  Plus,
  Rocket,
  Settings as SettingsIcon,
  ShieldCheck,
  Sparkles,
  ToggleRight,
  UsersRound,
  Zap,
  type LucideIcon,
} from "lucide-react"
import { Link } from "@tanstack/react-router"
import { FaLinkedin, FaSlack } from "react-icons/fa"
import { SiGithub, SiLinear, SiNotion, SiStripe, SiVercel } from "react-icons/si"

import { BrandMark } from "@/components/layout/BrandMark"

const features: { icon: LucideIcon; title: string; text: string }[] = [
  {
    icon: ToggleRight,
    title: "Feature Flags",
    text: "Toggle features on and off instantly, without deploying code.",
  },
  {
    icon: UsersRound,
    title: "Targeting & Segments",
    text: "Release features to specific users, cohorts, or segments.",
  },
  {
    icon: BarChart3,
    title: "Progressive Rollouts",
    text: "Gradually roll out features and monitor real-world impact.",
  },
  {
    icon: FlaskConical,
    title: "Experiments",
    text: "Run A/B tests and measure outcomes to make data-driven decisions.",
  },
  {
    icon: Network,
    title: "Environments",
    text: "Manage multiple environments with consistent configuration.",
  },
  {
    icon: ShieldCheck,
    title: "Approvals",
    text: "Add governance with approval workflows for sensitive changes.",
  },
]

const navItems: { label: string; icon: LucideIcon }[] = [
  { label: "Dashboard", icon: LayoutDashboard },
  { label: "Feature Flags", icon: ToggleRight },
  { label: "Environments", icon: Network },
  { label: "Segments", icon: UsersRound },
  { label: "Experiments", icon: FlaskConical },
  { label: "Approvals", icon: ShieldCheck },
  { label: "Projects", icon: Folder },
  { label: "Settings", icon: SettingsIcon },
]

const stats: { label: string; value: string; delta: string; detail: string }[] = [
  { label: "Feature Flags", value: "48", delta: "+12%", detail: "36 active · 4 retired · 4 draft" },
  { label: "Environments", value: "4", delta: "", detail: "Production, Staging, Dev, Test" },
  { label: "Segments", value: "12", delta: "", detail: "8 active · 4 retired" },
  { label: "Experiments", value: "6", delta: "+33%", detail: "3 running · 3 completed" },
]

const flagStatus: { label: string; count: number; color: string }[] = [
  { label: "Active", count: 36, color: "#19ad6c" },
  { label: "Retired", count: 8, color: "#94a3b8" },
  { label: "Draft", count: 4, color: "#2dd4bf" },
]

const recentActivity: { text: string; meta: string; colorClass: string }[] = [
  { text: "New feature flag checkout-v2 created", meta: "2 minutes ago by Sarah Chen", colorClass: "bg-blue-100 text-blue-600" },
  { text: "Experiment pricing-test started", meta: "16 minutes ago by Alex Kumar", colorClass: "bg-orange-100 text-orange-600" },
  { text: "Approval requested for search-redesign", meta: "1 hour ago by Arjun Patel", colorClass: "bg-purple-100 text-purple-600" },
  { text: "Flag dark-mode enabled in Production", meta: "3 hours ago by Daniel Park", colorClass: "bg-red-100 text-red-600" },
]

const quickActions: { label: string; icon: LucideIcon }[] = [
  { label: "Create Feature Flag", icon: GitBranch },
  { label: "Create Experiment", icon: UsersRound },
  { label: "Manage Segments", icon: Network },
  { label: "View Approvals", icon: ShieldCheck },
]

function DashboardMockup() {
  const activeCount = flagStatus.reduce((sum, s) => sum + s.count, 0)
  let cumulativePercent = 0
  const donutStops = flagStatus
    .map(({ color, count }) => {
      const start = cumulativePercent
      cumulativePercent += (count / activeCount) * 100
      return `${color} ${start}% ${cumulativePercent}%`
    })
    .join(", ")

  return (
    <div
      className="relative grid h-[485px] grid-cols-[110px_1fr] overflow-hidden rounded-xl border border-[#345b54] bg-[#0a2423] shadow-2xl max-[720px]:h-[415px] max-[720px]:grid-cols-[86px_1fr]"
      aria-label="LaunchFleet dashboard preview"
    >
      <div className="absolute inset-x-0 top-0 z-10 flex h-6 items-center gap-1.5 bg-[#071d1c] px-2.5">
        <span className="h-1.5 w-1.5 rounded-full bg-[#e05c4b]" />
        <span className="h-1.5 w-1.5 rounded-full bg-[#e0b84b]" />
        <span className="h-1.5 w-1.5 rounded-full bg-[#4be08a]" />
      </div>

      <aside className="col-start-1 flex flex-col gap-3 bg-[#071d1c] p-2 pt-8 text-[9px] text-[#9fb8b2]">
        <div className="flex items-center gap-1 px-1 text-white">
          <BrandMark /> <strong>LaunchFleet</strong>
        </div>
        <div className="mx-1 flex items-center justify-between rounded-sm bg-white/5 px-1.5 py-1">
          Acme Corp <ChevronDown size={10} />
        </div>
        <div className="mx-1 flex items-center justify-between rounded-sm bg-white/5 px-1.5 py-1">
          <span className="flex items-center gap-1">
            <span className="h-1.5 w-1.5 rounded-full bg-[#36e79a]" /> Production
          </span>
          <ChevronDown size={10} />
        </div>
        <div className="flex flex-1 flex-col gap-0.5">
          {navItems.map(({ label, icon: Icon }, index) => (
            <div
              className={`flex items-center gap-1.5 rounded-sm px-1.5 py-1 ${
                index === 0 ? "bg-[#19ad6c]/15 text-[#4be08a]" : ""
              }`}
              key={label}
            >
              <Icon size={11} /> {label}
            </div>
          ))}
        </div>
        <div className="flex items-center gap-1.5 px-1.5 py-1">
          <span className="grid h-4 w-4 place-items-center rounded-full bg-[#19ad6c] text-[8px] font-bold text-white">
            SC
          </span>
          <small className="leading-tight">
            Sarah Chen
            <br />
            <em className="text-[#6f8b85] not-italic">Admin</em>
          </small>
          <ChevronDown size={10} className="ml-auto" />
        </div>
      </aside>

      <main className="col-start-2 mt-6 overflow-hidden bg-[#f5f9fb] p-3 text-[#162329]">
        <div className="flex items-start justify-between">
          <div>
            <h3 className="text-[15px] font-semibold">Dashboard</h3>
            <p className="text-[10px] text-[#6b7a80]">
              Here&apos;s what&apos;s happening with your feature flags.
            </p>
          </div>
          <div className="flex items-center gap-1.5">
            <button className="inline-flex items-center gap-1 rounded-md border border-[#dde5e8] bg-white px-2 py-1 text-[9px]">
              Last 7 days <ChevronDown size={10} />
            </button>
            <button className="inline-flex items-center gap-1 rounded-md bg-[#19ad6c] px-2 py-1 text-[9px] font-semibold text-white">
              <Plus size={10} /> Create Flag
            </button>
          </div>
        </div>

        <div className="mt-2 grid grid-cols-4 gap-2">
          {stats.map(({ label, value, delta, detail }) => (
            <div className="rounded-md border border-[#dde5e8] bg-white p-2" key={label}>
              <span className="text-[9px] text-[#6b7a80]">{label}</span>
              <div className="flex items-baseline gap-1">
                <strong className="text-[15px]">{value}</strong>
                {delta && (
                  <i className="rounded-sm bg-[#19ad6c]/10 px-1 text-[8px] font-semibold text-[#19ad6c] not-italic">
                    {delta}
                  </i>
                )}
              </div>
              <small className="text-[7.5px] leading-tight text-[#8896a0]">{detail}</small>
            </div>
          ))}
        </div>

        <div className="mt-2 grid grid-cols-[1.5fr_1fr] gap-2">
          <div className="rounded-md border border-[#dde5e8] bg-white p-2.5">
            <div className="flex items-center justify-between">
              <span className="text-[10px] text-[#6b7a80]">
                Flag Evaluations{" "}
                <strong className="text-[#162329]">
                  1.2M <i className="text-[#19ad6c] not-italic">+24%</i>
                </strong>
              </span>
              <button className="inline-flex items-center gap-1 rounded-md border border-[#dde5e8] px-1.5 py-0.5 text-[8px]">
                Total Evaluations <ChevronDown size={9} />
              </button>
            </div>
            <svg viewBox="0 0 400 100" preserveAspectRatio="none" className="mt-1 h-16 w-full">
              <defs>
                <linearGradient id="area" x1="0" x2="0" y1="0" y2="1">
                  <stop offset="0" stopColor="#44e9a2" stopOpacity=".35" />
                  <stop offset="1" stopColor="#44e9a2" stopOpacity="0" />
                </linearGradient>
              </defs>
              <path
                d="M0 72 C40 62 54 78 95 63 S145 62 170 48 S220 60 250 43 S300 48 330 35 S365 52 400 29 V100 H0Z"
                fill="url(#area)"
              />
              <path
                d="M0 72 C40 62 54 78 95 63 S145 62 170 48 S220 60 250 43 S300 48 330 35 S365 52 400 29"
                fill="none"
                stroke="#19ad6c"
                strokeWidth="2.5"
              />
            </svg>
            <div className="mt-1 flex justify-between text-[7.5px] text-[#8896a0]">
              <span>Nov 10</span>
              <span>Nov 11</span>
              <span>Nov 12</span>
              <span>Nov 13</span>
              <span>Nov 14</span>
              <span>Nov 15</span>
              <span>Nov 16</span>
            </div>
          </div>

          <div className="rounded-md border border-[#dde5e8] bg-white p-2.5 max-[720px]:hidden">
            <strong className="text-[10px]">Flag Status</strong>
            <div className="mt-1.5 flex items-center gap-3">
              <div
                className="relative grid h-14 w-14 shrink-0 place-items-center rounded-full"
                style={{ background: `conic-gradient(${donutStops})` }}
              >
                <div className="grid h-8 w-8 place-items-center rounded-full bg-white text-center leading-none">
                  <strong className="text-[10px]">
                    {activeCount}
                    <small className="block text-[6px] font-normal text-[#8896a0]">Total</small>
                  </strong>
                </div>
              </div>
              <ul className="flex flex-col gap-1 text-[8.5px]">
                {flagStatus.map(({ label, count, color }) => (
                  <li className="flex items-center gap-1.5" key={label}>
                    <span className="h-1.5 w-1.5 rounded-full" style={{ backgroundColor: color }} />
                    {label} <span className="ml-auto font-semibold">{count}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>

        <div className="mt-2 grid grid-cols-[1.5fr_1fr] gap-2">
          <div className="rounded-md border border-[#dde5e8] bg-white p-2.5">
            <h4 className="text-[10px] font-semibold">Recent Activity</h4>
            <div className="mt-1.5 flex flex-col gap-1.5">
              {recentActivity.map(({ text, meta, colorClass }) => (
                <div className="flex items-start gap-1.5" key={text}>
                  <span className={`grid h-4 w-4 shrink-0 place-items-center rounded-full ${colorClass}`}>
                    <Zap size={9} />
                  </span>
                  <div className="text-[8.5px] leading-tight">
                    {text}
                    <small className="block text-[7px] text-[#8896a0]">{meta}</small>
                  </div>
                </div>
              ))}
            </div>
          </div>
          <div className="rounded-md border border-[#dde5e8] bg-white p-2.5 max-[720px]:hidden">
            <h4 className="text-[10px] font-semibold">Quick Actions</h4>
            <div className="mt-1.5 flex flex-col gap-1.5 text-[8.5px]">
              {quickActions.map(({ label, icon: Icon }) => (
                <div className="flex items-center gap-1.5" key={label}>
                  <Icon size={11} className="text-[#19ad6c]" /> {label}
                </div>
              ))}
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}

export function HomePage() {
  return (
    <div className="relative min-h-screen overflow-hidden bg-[#031c1b] font-sans text-[#f4f8f6]">
      <div className="absolute inset-0 h-[760px] bg-[radial-gradient(ellipse_at_75%_35%,#0b5440_0,transparent_44%),radial-gradient(ellipse_at_10%_80%,#07543c_0,transparent_38%),linear-gradient(135deg,#041c1b,#031c1b_70%)]" />

      <header
        className="relative z-10 mx-auto flex h-[84px] max-w-[1170px] items-center justify-between px-2
          max-[900px]:max-w-[calc(100%-36px)] max-[720px]:h-[70px]"
        id="top-header"
      >
        <a className="flex items-center gap-2.5 text-[21px] font-bold no-underline" href="#top">
          <BrandMark /> LaunchFleet
        </a>
        <nav className="flex gap-9 text-[13px] max-[720px]:hidden">
          <a className="inline-flex items-center gap-1 no-underline" href="#features">
            Product <ChevronDown size={13} />
          </a>
          <a className="no-underline" href="#pricing">
            Pricing
          </a>
          <a className="no-underline" href="#developers">
            Developers
          </a>
          <a className="inline-flex items-center gap-1 no-underline" href="#resources">
            Resources <ChevronDown size={13} />
          </a>
        </nav>
        <div className="flex items-center gap-7">
          <Link className="no-underline max-[720px]:hidden" to="/login">
            Sign in
          </Link>
        </div>
      </header>

      <section
        className="relative mx-auto grid min-h-[555px] max-w-[1170px] grid-cols-[385px_1fr] items-center gap-6 px-2 py-14
          max-[900px]:grid-cols-1 max-[900px]:max-w-[calc(100%-36px)] max-[720px]:max-w-[calc(100%-28px)] max-[720px]:py-8"
        id="top"
      >
        <div>
          <div className="inline-flex items-center gap-1.5 rounded-full border border-[#1f5044] bg-[#08302a] px-3 py-1 text-[11px]">
            <Sparkles size={13} className="text-[#36e79a]" /> Feature management for modern teams
          </div>
          <h1 className="my-6 text-[50px] leading-[1.03] font-bold tracking-[-2.4px] max-[720px]:text-[42px]">
            Build. Ship.
            <br />
            Experiment.
            <br />
            <span className="text-[#36e79a]">With Confidence.</span>
          </h1>
          <p className="max-w-[370px] text-[15px] leading-relaxed text-[#c3d3ce]">
            A feature management platform for high performing teams. Use feature flags, targeted
            rollouts, and experiments to ship better software, faster.
          </p>
          <div className="mt-6 flex gap-3">
            <a
              className="inline-flex items-center justify-center gap-3 rounded-lg bg-[#36e79a] px-6 py-3 text-[13px] font-bold text-[#021916] no-underline"
              href="#get-started"
            >
              Get started for free <ArrowRight size={18} />
            </a>
            <a
              className="inline-flex items-center justify-center gap-3 rounded-lg border border-[#31514c] px-6 py-3 text-[13px] font-bold no-underline"
              href="#demo"
            >
              <Play size={15} fill="currentColor" /> View demo
            </a>
          </div>
          <div className="mt-7 flex gap-4">
            <span className="flex items-center gap-1.5 text-[10px] whitespace-nowrap text-[#9fb8b2]">
              <Check size={14} className="text-[#36e79a]" /> Free for small teams
            </span>
            <span className="flex items-center gap-1.5 text-[10px] whitespace-nowrap text-[#9fb8b2]">
              <Check size={14} className="text-[#36e79a]" /> No credit card required
            </span>
            <span className="flex items-center gap-1.5 text-[10px] whitespace-nowrap text-[#9fb8b2]">
              <Check size={14} className="text-[#36e79a]" /> Setup in minutes
            </span>
          </div>
        </div>
        <DashboardMockup />
      </section>

      <section className="relative mx-auto max-w-[1170px] border-b border-[#133432] px-2 py-9 text-center max-[900px]:max-w-[calc(100%-36px)]">
        <p className="text-[11px] font-semibold tracking-[1.5px] text-[#6f8b85]">
          TRUSTED BY INNOVATIVE TEAMS
        </p>
        <div className="mt-5 flex justify-around text-[#aebdb9]">
          <strong className="flex items-center gap-2">
            <SiVercel size={16} /> Vercel
          </strong>
          <strong className="flex items-center gap-2">
            <SiLinear size={16} /> Linear
          </strong>
          <strong className="flex items-center gap-2">
            <SiNotion size={16} /> Notion
          </strong>
          <strong className="flex items-center gap-2">
            <FaSlack size={16} /> Slack
          </strong>
          <strong className="flex items-center gap-2">
            <SiGithub size={16} /> GitHub
          </strong>
          <strong className="flex items-center gap-2">
            <SiStripe size={16} /> Stripe
          </strong>
        </div>
      </section>

      <section
        className="relative mx-auto max-w-[1170px] px-1 py-12 max-[900px]:max-w-[calc(100%-36px)]"
        id="features"
      >
        <div className="text-center">
          <span className="text-[10px] font-bold tracking-[1.5px] text-[#36e79a]">
            WHY LAUNCHFLEET
          </span>
          <h2 className="mt-3 text-[32px] font-bold">
            Everything you need to <em className="text-[#36e79a] not-italic">ship with confidence</em>
          </h2>
          <p className="mx-auto mt-3 max-w-[520px] text-sm leading-relaxed text-[#9fb8b2]">
            From simple feature flags to advanced targeting and experimentation,
            <br /> LaunchFleet gives you the control and visibility to build better products.
          </p>
        </div>
        <div className="mt-6 grid grid-cols-3 gap-3 max-[900px]:grid-cols-2 max-[720px]:grid-cols-1">
          {features.map(({ icon: Icon, title, text }) => (
            <article
              className="min-h-[182px] rounded-[10px] border border-[#21504a] bg-[#092522] p-5"
              key={title}
            >
              <div className="grid h-12 w-12 place-items-center rounded-lg bg-[#064d3a] text-[#36e79a]">
                <Icon />
              </div>
              <h3 className="mt-3 font-semibold">{title}</h3>
              <p className="mt-1 text-[13px] leading-snug text-[#9fb8b2]">{text}</p>
            </article>
          ))}
        </div>
        <div className="mt-4 flex items-center gap-5 rounded-[10px] border border-[#08785c] bg-[#063f30] p-5">
          <div className="grid h-12 w-12 shrink-0 place-items-center rounded-lg bg-[#08483a] text-[#36e79a]">
            <Rocket />
          </div>
          <div>
            <h3 className="font-semibold">Ready to launch smarter?</h3>
            <p className="mt-1 text-sm text-[#9fb8b2]">
              Join hundreds of teams building better products with LaunchFleet.
            </p>
          </div>
          <a
            className="ml-auto inline-flex items-center justify-center gap-3 rounded-lg bg-[#36e79a] px-6 py-3 text-[13px] font-bold whitespace-nowrap text-[#021916] no-underline"
            href="#get-started"
          >
            Get started for free <ArrowRight size={18} />
          </a>
        </div>
      </section>

      <footer
        className="relative mx-auto flex max-w-[1170px] items-end justify-between px-2 py-5
          max-[720px]:max-w-[calc(100%-28px)] max-[720px]:items-start"
      >
        <div>
          <a className="flex items-center gap-2.5 text-[21px] font-bold no-underline" href="#top">
            <BrandMark /> LaunchFleet
          </a>
          <p className="mt-1 text-[13px] text-[#6f8b85]">Build. Ship. Experiment. With Confidence.</p>
        </div>
        <div className="flex gap-7 text-[11px] max-[720px]:hidden">
          <a className="no-underline" href="#product">
            Product
          </a>
          <a className="no-underline" href="#pricing">
            Pricing
          </a>
          <a className="no-underline" href="#developers">
            Developers
          </a>
          <a className="no-underline" href="#resources">
            Resources
          </a>
          <a className="no-underline" href="#docs">
            Docs
          </a>
        </div>
        <div className="text-right">
          <div className="flex justify-end gap-4 text-[#9fb8b2]">
            <SiGithub size={15} />
            <FaLinkedin size={15} />
            <Heart size={15} />
          </div>
          <p className="mt-2 text-[11px] text-[#6f8b85]">© 2024 LaunchFleet. All rights reserved.</p>
        </div>
      </footer>
    </div>
  )
}
