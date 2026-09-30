export const SCENARIO_TEMPLATES = [
  {
    id: 'failed-login',
    name: 'Failed Login Simulation',
    description: 'Generate repeated failed authentication events',
    category: 'Authentication',
    config: { attempts: 10, interval: 5, mode: 'realistic' }
  },
  {
    id: 'repeated-burst',
    name: 'Repeated Burst Simulation',
    description: 'Send login failures in rapid bursts',
    category: 'Authentication',
    config: { attempts: 30, interval: 0.3, mode: 'burst' }
  },
  {
    id: 'threshold-test',
    name: 'Threshold Test',
    description: 'Gently trigger the threat level thresholds',
    category: 'Authentication',
    config: { attempts: 7, interval: 4, mode: 'realistic' }
  },
  {
    id: 'multi-source',
    name: 'Multi-Source Simulation',
    description: 'Simulate from multiple source identities',
    category: 'Network',
    config: { attempts: 20, interval: 2, mode: 'realistic', multiSource: true }
  },
  {
    id: 'service-error',
    name: 'Service Error Simulation',
    description: 'Generate application/service error events',
    category: 'System',
    config: { attempts: 5, interval: 6, mode: 'realistic' }
  },
  {
    id: 'custom-json',
    name: 'Custom JSON Simulation',
    description: 'Use custom event JSON payload',
    category: 'Custom',
    config: { attempts: 10, interval: 5, mode: 'custom' }
  }
]

export const SOURCE_IDENTITIES = [
  { id: 'win-dc-01', os: 'Windows', osVersion: 'Windows Server 2019', ip: '192.168.1.24', description: 'Domain Controller', originating: 'ANVEX-LabSimulator', notes: 'Primary lab source for authentication simulations' },
  { id: 'win-fs-01', os: 'Windows', osVersion: 'Windows Server 2022', ip: '192.168.1.25', description: 'File Server', originating: 'ANVEX-LabSimulator', notes: '' },
  { id: 'lin-app-01', os: 'Linux', osVersion: 'Ubuntu 24.04 LTS', ip: '10.0.5.13', description: 'Application Server', originating: 'ANVEX-LabSimulator', notes: '' },
  { id: 'web-cl-01', os: 'Windows', osVersion: 'Windows 11', ip: '192.168.1.100', description: 'Workstation', originating: 'ANVEX-LabSimulator', notes: '' },
  { id: 'win-dm-01', os: 'Web', osVersion: 'Browser Agent', ip: '10.0.5.50', description: 'Test Client', originating: 'ANVEX-LabSimulator', notes: '' },
  { id: 'db-sim-01', os: 'Linux', osVersion: 'Debian 12', ip: '10.0.5.30', description: 'Database Server', originating: 'ANVEX-LabSimulator', notes: '' }
]

export const TARGET_SERVICES = [
  { id: 'anvex-core-auth', label: 'ANVEX Core Authentication', event: 4625 }
]

export const CATEGORIES = ['All', 'Authentication', 'Web', 'Network', 'System', 'Custom']
