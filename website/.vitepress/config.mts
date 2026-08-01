import { defineConfig, type DefaultTheme } from "vitepress";

const viNav: DefaultTheme.NavItem[] = [
  { text: "Trang chủ", link: "/" },
  { text: "Cài đặt", link: "/docs/installation" },
  { text: "Tài liệu", link: "/docs/" },
  { text: "Addon API", link: "/docs/addon-api" },
  { text: "indev", items: [
    { text: "Release status", link: "/docs/release-status" },
    { text: "GitHub releases", link: "https://github.com/Alexteens24/OpenTeams/releases" },
  ] },
];

const enNav: DefaultTheme.NavItem[] = [
  { text: "Home", link: "/en/" },
  { text: "Installation", link: "/en/docs/installation" },
  { text: "Documentation", link: "/en/docs/" },
  { text: "Addon API", link: "/en/docs/addon-api" },
  { text: "indev", items: [
    { text: "Release status", link: "/en/docs/release-status" },
    { text: "GitHub releases", link: "https://github.com/Alexteens24/OpenTeams/releases" },
  ] },
];

const viSidebar: DefaultTheme.SidebarItem[] = [
  { text: "Tổng quan", items: [
    { text: "Chào mừng", link: "/docs/" }, { text: "Tính năng", link: "/docs/features" },
    { text: "Trạng thái phát hành", link: "/docs/release-status" },
  ] },
  { text: "Bắt đầu", items: [
    { text: "Tải và build", link: "/docs/download" }, { text: "Cài đặt", link: "/docs/installation" },
    { text: "Hướng dẫn người chơi", link: "/docs/player-guide" },
  ] },
  { text: "Tra cứu", items: [
    { text: "Commands", link: "/docs/commands" }, { text: "Roles & permissions", link: "/docs/permissions" },
    { text: "Configuration", link: "/docs/configuration" }, { text: "UI & localization", link: "/docs/localization" },
  ] },
  { text: "Vận hành", items: [
    { text: "Database", link: "/docs/database" }, { text: "Production runbook", link: "/docs/operations" },
    { text: "Troubleshooting", link: "/docs/troubleshooting" },
  ] },
  { text: "Nhà phát triển", items: [
    { text: "Addon API", link: "/docs/addon-api" }, { text: "Extension points", link: "/docs/extensions" },
    { text: "Kiến trúc", link: "/docs/architecture" }, { text: "Development", link: "/docs/development" },
  ] },
];

const enSidebar: DefaultTheme.SidebarItem[] = [
  { text: "Overview", items: [
    { text: "Welcome", link: "/en/docs/" }, { text: "Features", link: "/en/docs/features" },
    { text: "Release status", link: "/en/docs/release-status" },
  ] },
  { text: "Getting started", items: [
    { text: "Download and build", link: "/en/docs/download" }, { text: "Installation", link: "/en/docs/installation" },
    { text: "Player guide", link: "/en/docs/player-guide" },
  ] },
  { text: "Reference", items: [
    { text: "Commands", link: "/en/docs/commands" }, { text: "Roles & permissions", link: "/en/docs/permissions" },
    { text: "Configuration", link: "/en/docs/configuration" }, { text: "UI & localization", link: "/en/docs/localization" },
  ] },
  { text: "Operations", items: [
    { text: "Database", link: "/en/docs/database" }, { text: "Production runbook", link: "/en/docs/operations" },
    { text: "Troubleshooting", link: "/en/docs/troubleshooting" },
  ] },
  { text: "Developers", items: [
    { text: "Addon API", link: "/en/docs/addon-api" }, { text: "Extension points", link: "/en/docs/extensions" },
    { text: "Architecture", link: "/en/docs/architecture" }, { text: "Development", link: "/en/docs/development" },
  ] },
];

export default defineConfig({
  title: "OpenTeams",
  titleTemplate: ":title · OpenTeams",
  description: "Correctness-first team platform for Paper and Folia",
  base: "/OpenTeams/",
  cleanUrls: true,
  lastUpdated: true,
  sitemap: { hostname: "https://alexteens24.github.io/OpenTeams/" },
  locales: {
    root: { label: "Tiếng Việt", lang: "vi-VN", description: "Nền tảng team correctness-first cho Paper và Folia" },
    en: { label: "English", lang: "en-US", link: "/en/", description: "Correctness-first team platform for Paper and Folia" },
  },
  head: [
    ["link", { rel: "icon", type: "image/svg+xml", href: "/OpenTeams/favicon.svg" }],
    ["meta", { name: "theme-color", content: "#0b1110" }],
    ["meta", { property: "og:type", content: "website" }],
    ["meta", { property: "og:title", content: "OpenTeams Documentation" }],
  ],
  themeConfig: {
    logo: "/favicon.svg",
    siteTitle: "OpenTeams",
    search: { provider: "local" },
    socialLinks: [{ icon: "github", link: "https://github.com/Alexteens24/OpenTeams" }],
    externalLinkIcon: true,
    locales: {
      root: {
        nav: viNav, sidebar: viSidebar,
        search: { provider: "local", options: { translations: {
          button: { buttonText: "Tìm kiếm", buttonAriaLabel: "Tìm trong tài liệu" },
          modal: { noResultsText: "Không tìm thấy kết quả cho", resetButtonTitle: "Xóa tìm kiếm", footer: { selectText: "chọn", navigateText: "di chuyển", closeText: "đóng" } },
        } } },
        outline: { level: [2, 4], label: "Trên trang này" },
        editLink: { pattern: "https://github.com/Alexteens24/OpenTeams/edit/main/website/:path", text: "Sửa trang này trên GitHub" },
        lastUpdated: { text: "Cập nhật lần cuối", formatOptions: { dateStyle: "medium", timeStyle: "short" } },
        docFooter: { prev: "Trang trước", next: "Trang sau" }, returnToTopLabel: "Lên đầu trang", sidebarMenuLabel: "Menu",
        darkModeSwitchLabel: "Giao diện", lightModeSwitchTitle: "Chuyển sang giao diện sáng", darkModeSwitchTitle: "Chuyển sang giao diện tối",
        footer: { message: "Phát hành theo giấy phép Apache-2.0.", copyright: "OpenTeams · correctness-first team platform" },
      },
      en: {
        nav: enNav, sidebar: enSidebar,
        outline: { level: [2, 4], label: "On this page" },
        editLink: { pattern: "https://github.com/Alexteens24/OpenTeams/edit/main/website/:path", text: "Edit this page on GitHub" },
        lastUpdated: { text: "Last updated", formatOptions: { dateStyle: "medium", timeStyle: "short" } },
        docFooter: { prev: "Previous page", next: "Next page" }, returnToTopLabel: "Return to top", sidebarMenuLabel: "Menu",
        darkModeSwitchLabel: "Appearance", lightModeSwitchTitle: "Switch to light theme", darkModeSwitchTitle: "Switch to dark theme",
        footer: { message: "Released under the Apache-2.0 License.", copyright: "OpenTeams · correctness-first team platform" },
      },
    },
  },
});
