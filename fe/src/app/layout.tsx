import type { Metadata } from "next";
import { Playfair_Display, Caveat, Gaegu } from "next/font/google";
import "./globals.css";
import NavbarWrapper from "@/components/common/NavbarWrapper";
import QueryProvider from "@/components/common/QueryProvider";

const playfair = Playfair_Display({
  variable: "--font-playfair",
  subsets: ["latin"],
  weight: ["400", "700", "900"],
  style: ["normal", "italic"],
  display: "swap",
});

const caveat = Caveat({
  variable: "--font-caveat",
  subsets: ["latin"],
  weight: ["400", "700"],
  display: "swap",
});

const gaegu = Gaegu({
  variable: "--font-gaegu",
  weight: ["400", "700"],
  display: "swap",
});

export const metadata: Metadata = {
  title: "The Interview",
  description: "AI가 진행하는 나만의 인터뷰 아카이브, 방구석 인터뷰",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body
        className={`${playfair.variable} ${caveat.variable} ${gaegu.variable} antialiased`}
      >
        <QueryProvider>
          <NavbarWrapper />
          {children}
        </QueryProvider>
      </body>
    </html>
  );
}
