import type { Metadata } from "next";
import { Playfair_Display } from "next/font/google";
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

export const metadata: Metadata = {
  title: "The Interview",
  description: "AI가 진행하는 나만의 인터뷰 아카이브, the interview",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body
        className={`${playfair.variable} antialiased`}
      >
        <QueryProvider>
          <NavbarWrapper />
          {children}
        </QueryProvider>
      </body>
    </html>
  );
}
