import { defineConfig } from "vitepress";

export default defineConfig({
  lang: "vi-VN",
  title: "OpenTeams",
  titleTemplate: ":title · OpenTeams",
  description: "Correctness-first team platform for Paper and Folia",
  base: "/OpenTeams/",
  cleanUrls: true,
  lastUpdated: true,
  sitemap: { hostname: "https://alexteens24.github.io/OpenTeams/" },
  head: [
    ["link", { rel: "icon", type: "image/svg+xml", href: "/OpenTeams/favicon.svg" }],
    ["meta", { name: "theme-color", content: "#0b1110" }],
    ["meta", { property: "og:type", content: "website" }],
    ["meta", { property: "og:title", content: "OpenTeams Documentation" }],
    ["meta", { property: "og:description", content: "Tài liệu đầy đủ cho OpenTeams trên Paper và Folia" }],
  ],
  themeConfig: {
    logo: "/favicon.svg",
    siteTitle: "OpenTeams",
    search: {
      provider: "local",
      options: {
        translations: {
          button: { buttonText: "Tìm kiếm", buttonAriaLabel: "Tìm trong tài liệu" },
          modal: {
            noResultsText: "Không tìm thấy kết quả cho",
            resetButtonTitle: "Xóa tìm kiếm",
            footer: { selectText: "chọn", navigateText: "di chuyển", closeText: "đóng" },
          },
        },
      },
    },
    nav: [
      { text: "Trang chủ", link: "/" },
      { text: "Cài đặt", link: "/docs/installation" },
      { text: "Tài liệu", link: "/docs/" },
      { text: "Addon API", link: "/docs/addon-api" },
      { text: "indev", items: [
        { text: "Release status", link: "/docs/release-status" },
        { text: "GitHub releases", link: "https://github.com/Alexteens24/OpenTeams/releases" },
      ] },
    ],
    sidebar: [
      {
        text: "Tổng quan",
        items: [
          { text: "Chào mừng", link: "/docs/" },
          { text: "Tính năng", link: "/docs/features" },
          { text: "Trạng thái phát hành", link: "/docs/release-status" },
        ],
      },
      {
        text: "Bắt đầu",
        items: [
          { text: "Tải và build", link: "/docs/download" },
          { text: "Cài đặt", link: "/docs/installation" },
          { text: "Hướng dẫn người chơi", link: "/docs/player-guide" },
        ],
      },
      {
        text: "Tra cứu",
        items: [
          { text: "Commands", link: "/docs/commands" },
          { text: "Roles & permissions", link: "/docs/permissions" },
          { text: "Configuration", link: "/docs/configuration" },
          { text: "UI & localization", link: "/docs/localization" },
        ],
      },
      {
        text: "Vận hành",
        items: [
          { text: "Database", link: "/docs/database" },
          { text: "Production runbook", link: "/docs/operations" },
          { text: "Troubleshooting", link: "/docs/troubleshooting" },
        ],
      },
      {
        text: "Nhà phát triển",
        items: [
          { text: "Addon API", link: "/docs/addon-api" },
          { text: "Extension points", link: "/docs/extensions" },
          { text: "Kiến trúc", link: "/docs/architecture" },
          { text: "Development", link: "/docs/development" },
        ],
      },
    ],
    socialLinks: [
      { icon: "github", link: "https://github.com/Alexteens24/OpenTeams" },
    ],
    outline: { level: [2, 4], label: "Trên trang này" },
    editLink: {
      pattern: "https://github.com/Alexteens24/OpenTeams/edit/main/website/:path",
      text: "Sửa trang này trên GitHub",
    },
    lastUpdated: { text: "Cập nhật lần cuối", formatOptions: { dateStyle: "medium", timeStyle: "short" } },
    docFooter: { prev: "Trang trước", next: "Trang sau" },
    returnToTopLabel: "Lên đầu trang",
    sidebarMenuLabel: "Menu",
    darkModeSwitchLabel: "Giao diện",
    lightModeSwitchTitle: "Chuyển sang giao diện sáng",
    darkModeSwitchTitle: "Chuyển sang giao diện tối",
    externalLinkIcon: true,
    footer: {
      message: "Phát hành theo giấy phép Apache-2.0.",
      copyright: "OpenTeams · correctness-first team platform",
    },
  },
});
