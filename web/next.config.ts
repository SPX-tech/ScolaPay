import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  output: 'standalone',
  async rewrites() {
    return process.env.NODE_ENV === 'development'
      ? [{ source: '/api/:path*', destination: 'http://127.0.0.1:3000/api/:path*' }]
      : [];
  },
};

export default nextConfig;
