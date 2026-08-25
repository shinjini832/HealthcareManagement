import React, { useState, useEffect } from 'react';
import api from './api';
import { 
  Calendar, 
  Clock, 
  FileText, 
  Stethoscope, 
  Search,
  CheckCircle,
  Plus,
  Trash2,
  AlertTriangle,
  User,
  Mail,
  Lock,
  LogOut,
  CalendarDays
} from 'lucide-react';

// Interfaces
interface UserInfo {
  token: string;
  email: string;
  fullName: string;
  role: 'PATIENT' | 'DOCTOR' | 'ADMIN';
}

interface Doctor {
  id: number;
  userId: number;
  fullName: string;
  email: string;
  specialization: string;
  workingHoursStart: string;
  workingHoursEnd: string;
  slotDurationMinutes: number;
}

interface Slot {
  startTime: string;
  endTime: string;
  available: boolean;
}

interface Prescription {
  medicationName: string;
  dosage: string;
  frequency: string;
  durationDays: number;
}

interface Appointment {
  appointmentId: number;
  patientEmail: string;
  patientName: string;
  doctorId: number;
  doctorName: string;
  doctorSpecialization: string;
  appointmentDate: string;
  startTime: string;
  endTime: string;
  status: 'HELD' | 'CONFIRMED' | 'CANCELLED' | 'CANCELLED_BY_DOCTOR_LEAVE';
  urgencyLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  patientSymptoms?: string;
  preVisitSummary?: string;
  postVisitNotes?: string;
  postVisitSummary?: string;
  prescriptions?: Prescription[];
}

interface Leave {
  id: number;
  doctorId: number;
  leaveDate: string;
  reason: string;
}

function App() {
  // Auth state
  const [user, setUser] = useState<UserInfo | null>(null);
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login');
  
  // Auth Form Fields
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [role, setRole] = useState<'PATIENT' | 'DOCTOR' | 'ADMIN'>('PATIENT');
  const [authError, setAuthError] = useState('');

  // General Portal state
  const [activeTab, setActiveTab] = useState('dashboard');
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  // Patient State
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [searchSpec, setSearchSpec] = useState('');
  const [selectedDoctor, setSelectedDoctor] = useState<Doctor | null>(null);
  const [bookingDate, setBookingDate] = useState('');
  const [availableSlots, setAvailableSlots] = useState<Slot[]>([]);
  const [selectedSlot, setSelectedSlot] = useState<Slot | null>(null);
  const [symptoms, setSymptoms] = useState('');
  const [urgency, setUrgency] = useState<'LOW' | 'MEDIUM' | 'HIGH'>('MEDIUM');
  const [holdId, setHoldId] = useState<number | string | null>(null);
  const [patientAppointments, setPatientAppointments] = useState<Appointment[]>([]);

  // Doctor State
  const [doctorAppointments, setDoctorAppointments] = useState<Appointment[]>([]);
  const [selectedAppointment, setSelectedAppointment] = useState<Appointment | null>(null);
  const [clinicalNotes, setClinicalNotes] = useState('');
  const [prescriptions, setPrescriptions] = useState<Prescription[]>([]);
  const [medName, setMedName] = useState('');
  const [medDosage, setMedDosage] = useState('');
  const [medFreq, setMedFreq] = useState('ONCE_DAILY');
  const [medDuration, setMedDuration] = useState(5);

  // Admin State
  const [adminDoctors, setAdminDoctors] = useState<Doctor[]>([]);
  const [conflicts, setConflicts] = useState<Appointment[]>([]);
  const [newDocName, setNewDocName] = useState('');
  const [newDocEmail, setNewDocEmail] = useState('');
  const [newDocPassword, setNewDocPassword] = useState('');
  const [newDocSpec, setNewDocSpec] = useState('');
  const [newDocHoursStart, setNewDocHoursStart] = useState('09:00');
  const [newDocHoursEnd, setNewDocHoursEnd] = useState('17:00');
  const [newDocDuration, setNewDocDuration] = useState(30);
  const [editingDoc, setEditingDoc] = useState<Doctor | null>(null);

  // Leave State
  const [leaveDocId, setLeaveDocId] = useState<number | string>('');
  const [leaveDate, setLeaveDate] = useState('');
  const [leaveReason, setLeaveReason] = useState('');
  const [doctorLeaves, setDoctorLeaves] = useState<Leave[]>([]);

  // Load user from localStorage on init
  useEffect(() => {
    const savedToken = localStorage.getItem('token');
    const savedEmail = localStorage.getItem('email');
    const savedName = localStorage.getItem('fullName');
    const savedRole = localStorage.getItem('role') as UserInfo['role'];

    if (savedToken && savedEmail && savedName && savedRole) {
      setUser({
        token: savedToken,
        email: savedEmail,
        fullName: savedName,
        role: savedRole
      });
    }
  }, []);

  // Fetch portal specific data when user logs in or shifts tabs
  useEffect(() => {
    if (!user) return;
    setErrorMsg('');
    setSuccessMsg('');

    if (user.role === 'PATIENT') {
      fetchPatientDashboard();
    } else if (user.role === 'DOCTOR') {
      fetchDoctorDashboard();
    } else if (user.role === 'ADMIN') {
      fetchAdminDashboard();
    }
  }, [user, activeTab]);

  const handleLogout = () => {
    localStorage.clear();
    setUser(null);
    setDoctors([]);
    setPatientAppointments([]);
    setDoctorAppointments([]);
    setAdminDoctors([]);
    setConflicts([]);
    setSelectedDoctor(null);
    setSelectedAppointment(null);
    setEditingDoc(null);
  };

  // Auth Operations
  const handleAuthSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError('');
    try {
      if (authMode === 'login') {
        const response = await api.post('/auth/login', { email, password });
        const data = response.data;
        localStorage.setItem('token', data.token);
        localStorage.setItem('email', data.email);
        localStorage.setItem('fullName', data.fullName);
        localStorage.setItem('role', data.role);
        setUser({
          token: data.token,
          email: data.email,
          fullName: data.fullName,
          role: data.role as UserInfo['role']
        });
      } else {
        const response = await api.post('/auth/register', { email, password, fullName, role });
        const data = response.data;
        localStorage.setItem('token', data.token);
        localStorage.setItem('email', data.email);
        localStorage.setItem('fullName', data.fullName);
        localStorage.setItem('role', data.role);
        setUser({
          token: data.token,
          email: data.email,
          fullName: data.fullName,
          role: data.role as UserInfo['role']
        });
      }
      setEmail('');
      setPassword('');
      setFullName('');
    } catch (err: any) {
      setAuthError(err.response?.data || 'Authentication failed. Please check credentials.');
    }
  };

  // Patient Dashboard Loads
  const fetchPatientDashboard = async () => {
    try {
      // 1. Fetch search/doctors list
      const docResponse = await api.get(`/doctors?specialization=${searchSpec}`);
      setDoctors(docResponse.data);

      // 2. Fetch patient appointments
      const appResponse = await api.get('/appointments/patient');
      setPatientAppointments(appResponse.data);
    } catch (err: any) {
      setErrorMsg('Failed to load patient dashboard.');
    }
  };

  // Fetch Slots
  const fetchSlots = async (docId: number, dateStr: string) => {
    if (!docId || !dateStr) return;
    try {
      setSelectedSlot(null);
      setAvailableSlots([]);
      const response = await api.get(`/appointments/slots?doctorId=${docId}&date=${dateStr}`);
      setAvailableSlots(response.data);
    } catch (err: any) {
      setErrorMsg('Could not load slots.');
    }
  };

  // Place Slot Hold
  const handleHoldSlot = async (slot: Slot) => {
    if (!selectedDoctor || !bookingDate) return;
    try {
      const response = await api.post('/appointments/hold', {
        doctorId: selectedDoctor.id,
        slotDate: bookingDate,
        startTime: slot.startTime
      });
      setHoldId(response.data.holdId);
      setSelectedSlot(slot);
      setSuccessMsg('Slot successfully held for 5 minutes! Fill symptoms below to confirm.');
    } catch (err: any) {
      setErrorMsg(err.response?.data || 'Failed to place hold on slot.');
    }
  };

  // Confirm Appointment Booking
  const handleConfirmBooking = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!holdId) return;
    try {
      await api.post('/appointments/confirm', {
        slotHoldId: holdId,
        patientSymptoms: symptoms,
        urgencyLevel: urgency
      });
      setSuccessMsg('Appointment Booked and Synced with Google Calendar! Notification email queued.');
      setHoldId(null);
      setSelectedSlot(null);
      setSelectedDoctor(null);
      setSymptoms('');
      setBookingDate('');
      fetchPatientDashboard();
    } catch (err: any) {
      setErrorMsg(err.response?.data || 'Failed to confirm booking.');
    }
  };

  // Doctor Dashboard Loads
  const fetchDoctorDashboard = async () => {
    try {
      const response = await api.get('/appointments/doctor');
      setDoctorAppointments(response.data);
    } catch (err: any) {
      setErrorMsg('Failed to load doctor schedule.');
    }
  };

  // Prescription List Builder helpers
  const handleAddPrescription = () => {
    if (!medName || !medDosage) return;
    setPrescriptions([...prescriptions, {
      medicationName: medName,
      dosage: medDosage,
      frequency: medFreq,
      durationDays: medDuration
    }]);
    setMedName('');
    setMedDosage('');
    setMedFreq('ONCE_DAILY');
    setMedDuration(5);
  };

  const handleRemovePrescription = (index: number) => {
    setPrescriptions(prescriptions.filter((_, i) => i !== index));
  };

  // Complete Appointment Visit notes + AI summaries + prescriptions
  const handleCompleteVisitSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAppointment) return;
    try {
      await api.post(`/appointments/${selectedAppointment.appointmentId}/complete`, {
        postVisitNotes: clinicalNotes,
        prescriptions: prescriptions
      });
      setSuccessMsg('Visit completed successfully! Post-visit summary and prescriptions saved.');
      setClinicalNotes('');
      setPrescriptions([]);
      setSelectedAppointment(null);
      fetchDoctorDashboard();
    } catch (err: any) {
      setErrorMsg(err.response?.data || 'Failed to complete visit.');
    }
  };

  // Admin Dashboard Loads
  const fetchAdminDashboard = async () => {
    try {
      const docsResponse = await api.get('/admin/doctors');
      setAdminDoctors(docsResponse.data);

      const conflictsResponse = await api.get('/admin/doctors/conflicts');
      setConflicts(conflictsResponse.data);
    } catch (err: any) {
      setErrorMsg('Failed to load admin controls.');
    }
  };

  // Admin Actions: Register Doctor
  const handleRegisterDoctorSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await api.post('/admin/doctors', {
        fullName: newDocName,
        email: newDocEmail,
        password: newDocPassword,
        specialization: newDocSpec,
        workingHoursStart: newDocHoursStart,
        workingHoursEnd: newDocHoursEnd,
        slotDurationMinutes: newDocDuration
      });
      setSuccessMsg('New doctor profile successfully registered!');
      setNewDocName('');
      setNewDocEmail('');
      setNewDocPassword('');
      setNewDocSpec('');
      fetchAdminDashboard();
    } catch (err: any) {
      setErrorMsg(err.response?.data || 'Failed to register doctor profile.');
    }
  };

  // Admin Actions: Update Doctor
  const handleUpdateDoctorSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingDoc) return;
    try {
      await api.put(`/admin/doctors/${editingDoc.id}`, {
        specialization: editingDoc.specialization,
        workingHoursStart: editingDoc.workingHoursStart,
        workingHoursEnd: editingDoc.workingHoursEnd,
        slotDurationMinutes: editingDoc.slotDurationMinutes
      });
      setSuccessMsg('Doctor profile successfully updated.');
      setEditingDoc(null);
      fetchAdminDashboard();
    } catch (err: any) {
      setErrorMsg(err.response?.data || 'Failed to update doctor profile.');
    }
  };

  // Admin Actions: Doctor Leaves & Conflict Cancellation
  const handleAddLeaveSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!leaveDocId || !leaveDate) return;
    try {
      await api.post(`/admin/doctors/${leaveDocId}/leaves`, {
        leaveDate: leaveDate,
        reason: leaveReason
      });
      setSuccessMsg('Leave registered successfully! All conflicting appointments were cancelled.');
      setLeaveDocId('');
      setLeaveDate('');
      setLeaveReason('');
      fetchAdminDashboard();
    } catch (err: any) {
      setErrorMsg(err.response?.data || 'Failed to register leave.');
    }
  };

  const handleFetchLeaves = async (docId: number) => {
    try {
      const response = await api.get(`/admin/doctors/${docId}/leaves`);
      setDoctorLeaves(response.data);
    } catch (err: any) {
      setErrorMsg('Failed to load leaves.');
    }
  };

  const handleDeleteLeave = async (docId: number, leaveId: number) => {
    try {
      await api.delete(`/admin/doctors/${docId}/leaves/${leaveId}`);
      setSuccessMsg('Leave cancelled successfully.');
      handleFetchLeaves(docId);
    } catch (err: any) {
      setErrorMsg('Failed to cancel leave.');
    }
  };

  // Logged Out Component
  if (!user) {
    return (
      <div className="app-container" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', backgroundColor: 'var(--bg-primary)' }}>
        <div className="clinic-card" style={{ width: '100%', maxWidth: '420px', padding: '2.5rem 2rem' }}>
          <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
            <div style={{ display: 'inline-flex', backgroundColor: 'var(--primary-light)', padding: '1rem', borderRadius: '50%', color: 'var(--primary)', marginBottom: '1rem' }}>
              <Stethoscope size={36} />
            </div>
            <h1 style={{ fontSize: '1.8rem', fontWeight: 700, color: 'var(--text-dark)' }}>CareFlow Portal</h1>
            <p style={{ color: 'var(--text-light)', fontSize: '0.9rem', marginTop: '0.25rem' }}>Your Integrated Healthcare Management Hub</p>
          </div>

          <form onSubmit={handleAuthSubmit}>
            {authMode === 'register' && (
              <div className="form-group">
                <label className="form-label">Full Name</label>
                <div style={{ position: 'relative' }}>
                  <input 
                    type="text" 
                    className="form-input" 
                    placeholder="Enter full name" 
                    style={{ paddingLeft: '2.5rem', width: '100%' }}
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    required
                  />
                  <User size={16} style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-light)' }} />
                </div>
              </div>
            )}

            <div className="form-group">
              <label className="form-label">Email Address</label>
              <div style={{ position: 'relative' }}>
                <input 
                  type="email" 
                  className="form-input" 
                  placeholder="name@healthcare.com" 
                  style={{ paddingLeft: '2.5rem', width: '100%' }}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
                <Mail size={16} style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-light)' }} />
              </div>
            </div>

            <div className="form-group" style={{ marginBottom: '1.5rem' }}>
              <label className="form-label">Password</label>
              <div style={{ position: 'relative' }}>
                <input 
                  type="password" 
                  className="form-input" 
                  placeholder="••••••••" 
                  style={{ paddingLeft: '2.5rem', width: '100%' }}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
                <Lock size={16} style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-light)' }} />
              </div>
            </div>

            {authMode === 'register' && (
              <div className="form-group" style={{ marginBottom: '1.5rem' }}>
                <label className="form-label">System Access Role</label>
                <select 
                  className="form-input" 
                  style={{ width: '100%' }} 
                  value={role} 
                  onChange={(e) => setRole(e.target.value as UserInfo['role'])}
                >
                  <option value="PATIENT">Patient</option>
                  <option value="DOCTOR">Doctor Profile</option>
                  <option value="ADMIN">Administrator</option>
                </select>
              </div>
            )}

            {authError && (
              <div style={{ backgroundColor: 'var(--error-light)', color: 'var(--error)', padding: '0.75rem', borderRadius: 'var(--radius-sm)', fontSize: '0.85rem', fontWeight: 500, marginBottom: '1rem' }}>
                {authError}
              </div>
            )}

            <button className="btn btn-primary" style={{ width: '100%', padding: '0.9rem' }}>
              {authMode === 'login' ? 'Sign In' : 'Create Account'}
            </button>
          </form>

          <div style={{ textAlign: 'center', marginTop: '1.5rem' }}>
            <span style={{ fontSize: '0.85rem', color: 'var(--text-medium)' }}>
              {authMode === 'login' ? "Don't have an account?" : 'Already registered?'}
            </span>
            <button 
              className="btn" 
              style={{ background: 'none', border: 'none', color: 'var(--primary-hover)', fontWeight: 600, padding: '0 0.5rem', fontSize: '0.85rem' }}
              onClick={() => {
                setAuthMode(authMode === 'login' ? 'register' : 'login');
                setAuthError('');
              }}
            >
              {authMode === 'login' ? 'Sign up' : 'Sign in'}
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="app-container">
      {/* Dynamic Header based on Role */}
      <header className="header">
        <div className="logo">
          <Stethoscope size={28} />
          <span>CareFlow Portal</span>
          <span style={{ fontSize: '0.75rem', backgroundColor: 'var(--primary-light)', padding: '0.25rem 0.5rem', borderRadius: 'var(--radius-sm)', fontWeight: 600, color: 'var(--primary-hover)', textTransform: 'uppercase' }}>
            {user.role}
          </span>
        </div>
        <nav style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
          <ul className="nav-links">
            <li>
              <a 
                href="#dashboard" 
                className={`nav-link ${activeTab === 'dashboard' ? 'active' : ''}`}
                onClick={() => setActiveTab('dashboard')}
              >
                Dashboard
              </a>
            </li>
            
            {user.role === 'PATIENT' && (
              <li>
                <a 
                  href="#book" 
                  className={`nav-link ${activeTab === 'book' ? 'active' : ''}`}
                  onClick={() => { setActiveTab('book'); setSelectedDoctor(null); }}
                >
                  Schedule Appointment
                </a>
              </li>
            )}

            {user.role === 'ADMIN' && (
              <>
                <li>
                  <a 
                    href="#leaves" 
                    className={`nav-link ${activeTab === 'leaves' ? 'active' : ''}`}
                    onClick={() => setActiveTab('leaves')}
                  >
                    Manage Leaves
                  </a>
                </li>
                <li>
                  <a 
                    href="#conflicts" 
                    className={`nav-link ${activeTab === 'conflicts' ? 'active' : ''}`}
                    onClick={() => setActiveTab('conflicts')}
                  >
                    Conflicts ({conflicts.length})
                  </a>
                </li>
              </>
            )}
          </ul>

          <div style={{ height: '24px', width: '1px', backgroundColor: 'var(--border)' }}></div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            <span style={{ fontSize: '0.9rem', color: 'var(--text-medium)', fontWeight: 500 }}>
              {user.fullName}
            </span>
            <button className="btn btn-outline" style={{ padding: '0.5rem 0.75rem', display: 'flex', gap: '0.25rem' }} onClick={handleLogout}>
              <LogOut size={16} />
              Logout
            </button>
          </div>
        </nav>
      </header>

      {/* Main Grid */}
      <main className="main-content">
        
        {/* Flash Notifications */}
        {successMsg && (
          <div style={{ backgroundColor: 'var(--success-light)', borderLeft: '4px solid var(--success)', color: 'var(--success)', padding: '1rem', borderRadius: 'var(--radius-sm)', marginBottom: '1.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontWeight: 500 }}>{successMsg}</span>
            <button style={{ background: 'none', border: 'none', color: 'var(--success)', fontWeight: 'bold', cursor: 'pointer' }} onClick={() => setSuccessMsg('')}>✕</button>
          </div>
        )}
        {errorMsg && (
          <div style={{ backgroundColor: 'var(--error-light)', borderLeft: '4px solid var(--error)', color: 'var(--error)', padding: '1rem', borderRadius: 'var(--radius-sm)', marginBottom: '1.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontWeight: 500 }}>{errorMsg}</span>
            <button style={{ background: 'none', border: 'none', color: 'var(--error)', fontWeight: 'bold', cursor: 'pointer' }} onClick={() => setErrorMsg('')}>✕</button>
          </div>
        )}

        {/* ------------------- PATIENT PORTAL ------------------- */}
        {user.role === 'PATIENT' && (
          <>
            {activeTab === 'dashboard' && (
              <div>
                <div style={{ marginBottom: '2rem' }}>
                  <h2 style={{ fontSize: '1.8rem', fontWeight: 700 }}>Upcoming & Past Clinic Visits</h2>
                  <p style={{ color: 'var(--text-medium)' }}>Track your summaries, prescriptions, and status in real-time.</p>
                </div>

                <div className="card-grid">
                  {patientAppointments.length === 0 ? (
                    <div className="clinic-card" style={{ gridColumn: '1 / -1', textAlign: 'center', padding: '3rem 2rem' }}>
                      <CalendarDays size={48} style={{ color: 'var(--text-light)', marginBottom: '1rem' }} />
                      <h3 style={{ fontSize: '1.2rem', fontWeight: 600 }}>No Appointments Booked</h3>
                      <p style={{ color: 'var(--text-medium)', fontSize: '0.9rem', marginTop: '0.25rem' }}>Schedule your first visit by clicking the schedule button above.</p>
                    </div>
                  ) : (
                    patientAppointments.map(app => (
                      <div key={app.appointmentId} className="clinic-card">
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1rem' }}>
                          <div>
                            <span style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--primary-hover)', textTransform: 'uppercase' }}>
                              {app.doctorSpecialization}
                            </span>
                            <h3 style={{ fontSize: '1.25rem', fontWeight: 600, marginTop: '0.1rem' }}>Dr. {app.doctorName}</h3>
                          </div>
                          <span className={`badge ${
                            app.status === 'CONFIRMED' ? 'badge-confirmed' : app.status === 'HELD' ? 'badge-held' : 'badge-cancelled'
                          }`}>
                            {app.status.replace(/_/g, ' ')}
                          </span>
                        </div>

                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '1rem', color: 'var(--text-medium)', fontSize: '0.85rem', marginBottom: '1rem', borderBottom: '1px solid var(--border)', paddingBottom: '1rem' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                            <Calendar size={14} color="var(--primary)" />
                            <span>{app.appointmentDate}</span>
                          </div>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                            <Clock size={14} color="var(--primary)" />
                            <span>{app.startTime} - {app.endTime}</span>
                          </div>
                          <div>
                            <span style={{ fontWeight: 600, color: app.urgencyLevel === 'HIGH' ? 'var(--error)' : 'var(--text-medium)' }}>
                              Urgency: {app.urgencyLevel}
                            </span>
                          </div>
                        </div>

                        {app.preVisitSummary && (
                          <div style={{ marginBottom: '1rem' }}>
                            <h4 style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-medium)' }}>AI Pre-Visit Symptoms Summary:</h4>
                            <p style={{ fontSize: '0.9rem', color: 'var(--text-dark)', backgroundColor: 'var(--bg-primary)', padding: '0.75rem', borderRadius: 'var(--radius-sm)', marginTop: '0.25rem', whiteSpace: 'pre-wrap' }}>
                              {app.preVisitSummary}
                            </p>
                          </div>
                        )}

                        {app.postVisitSummary && (
                          <div style={{ marginBottom: '1rem' }}>
                            <h4 style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--success)' }}>AI Patient-Friendly Summary:</h4>
                            <p style={{ fontSize: '0.9rem', color: 'var(--text-dark)', backgroundColor: 'var(--success-light)', padding: '0.75rem', borderRadius: 'var(--radius-sm)', marginTop: '0.25rem', whiteSpace: 'pre-wrap' }}>
                              {app.postVisitSummary}
                            </p>
                          </div>
                        )}

                        {app.prescriptions && app.prescriptions.length > 0 && (
                          <div>
                            <h4 style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-medium)', marginBottom: '0.4rem' }}>Medication Prescriptions:</h4>
                            <ul style={{ listStyle: 'none', paddingLeft: 0 }}>
                              {app.prescriptions.map((p, idx) => (
                                <li key={idx} style={{ fontSize: '0.85rem', backgroundColor: '#f1f5f9', padding: '0.4rem 0.75rem', borderRadius: 'var(--radius-sm)', display: 'flex', justifyContent: 'space-between', marginBottom: '0.25rem' }}>
                                  <span style={{ fontWeight: 600 }}>{p.medicationName} ({p.dosage})</span>
                                  <span style={{ color: 'var(--text-medium)' }}>{p.frequency.replace(/_/g, ' ')} for {p.durationDays}d</span>
                                </li>
                              ))}
                            </ul>
                          </div>
                        )}
                      </div>
                    ))
                  )}
                </div>
              </div>
            )}

            {activeTab === 'book' && (
              <div>
                <div style={{ marginBottom: '2rem' }}>
                  <h2 style={{ fontSize: '1.8rem', fontWeight: 700 }}>Schedule Medical Appointment</h2>
                  <p style={{ color: 'var(--text-medium)' }}>Search your doctor, secure a temporary hold, and confirm your slot.</p>
                </div>

                {!selectedDoctor ? (
                  <div>
                    {/* Doctor Search input */}
                    <div className="clinic-card" style={{ marginBottom: '1.5rem', display: 'flex', gap: '1rem', alignItems: 'center' }}>
                      <div className="form-group" style={{ flex: 1, marginBottom: 0 }}>
                        <div style={{ position: 'relative' }}>
                          <input 
                            type="text" 
                            className="form-input" 
                            placeholder="Search doctors by specialization (e.g. Cardiology, Neurology)..."
                            style={{ paddingLeft: '2.5rem', width: '100%' }}
                            value={searchSpec}
                            onChange={(e) => setSearchSpec(e.target.value)}
                          />
                          <Search size={16} style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)', color: 'var(--text-light)' }} />
                        </div>
                      </div>
                      <button className="btn btn-primary" onClick={fetchPatientDashboard}>Search</button>
                    </div>

                    <div className="card-grid">
                      {doctors.map(doc => (
                        <div key={doc.id} className="clinic-card">
                          <h3 style={{ fontSize: '1.25rem', fontWeight: 600 }}>Dr. {doc.fullName}</h3>
                          <p style={{ color: 'var(--primary-hover)', fontWeight: 600, fontSize: '0.9rem', marginBottom: '0.75rem' }}>{doc.specialization}</p>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--text-medium)', fontSize: '0.85rem', marginBottom: '1.5rem' }}>
                            <Clock size={16} />
                            <span>Hours: {doc.workingHoursStart} - {doc.workingHoursEnd} ({doc.slotDurationMinutes} min slots)</span>
                          </div>
                          <button className="btn btn-primary" style={{ width: '100%' }} onClick={() => { setSelectedDoctor(doc); setBookingDate(''); setAvailableSlots([]); }}>
                            View Available Slots
                          </button>
                        </div>
                      ))}
                    </div>
                  </div>
                ) : (
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '2rem', flexWrap: 'wrap' }}>
                    
                    {/* Left Panel: Slot Selector */}
                    <div className="clinic-card">
                      <button className="btn btn-outline" style={{ marginBottom: '1rem' }} onClick={() => setSelectedDoctor(null)}>← Back to Doctors</button>
                      <h3 style={{ fontSize: '1.3rem', fontWeight: 600, marginBottom: '0.5rem' }}>Dr. {selectedDoctor.fullName}</h3>
                      <p style={{ color: 'var(--text-medium)', fontSize: '0.9rem', marginBottom: '1.5rem' }}>Select your appointment date below to fetch available slots.</p>

                      <div className="form-group" style={{ marginBottom: '1.5rem' }}>
                        <label className="form-label">Appointment Date</label>
                        <input 
                          type="date" 
                          className="form-input" 
                          value={bookingDate}
                          onChange={(e) => { setBookingDate(e.target.value); fetchSlots(selectedDoctor.id, e.target.value); }}
                          min={(() => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; })()}
                        />
                      </div>

                      {bookingDate && (
                        <div>
                          <h4 style={{ fontSize: '0.9rem', fontWeight: 600, color: 'var(--text-medium)', marginBottom: '0.75rem' }}>Available Time Slots:</h4>
                          {availableSlots.length === 0 ? (
                            <p style={{ fontSize: '0.9rem', color: 'var(--text-light)' }}>No slots available on this date. Select another date or doctor.</p>
                          ) : availableSlots.every(s => !s.available) ? (
                            <p style={{ fontSize: '0.9rem', color: 'var(--text-light)' }}>All slots are fully booked or have passed for this date. Please select another date.</p>
                          ) : (
                            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(110px, 1fr))', gap: '0.75rem' }}>
                              {availableSlots.map((slot, idx) => (
                                <button 
                                  key={idx} 
                                  className={`btn ${selectedSlot?.startTime === slot.startTime ? 'btn-primary' : 'btn-outline'}`}
                                  style={{ padding: '0.5rem', fontSize: '0.8rem', opacity: slot.available ? 1 : 0.35, cursor: slot.available ? 'pointer' : 'not-allowed' }}
                                  disabled={!slot.available}
                                  onClick={() => handleHoldSlot(slot)}
                                  title={slot.available ? '' : 'This slot is already booked or has passed'}
                                >
                                  {slot.startTime}
                                </button>
                              ))}
                            </div>
                          )}
                        </div>
                      )}
                    </div>

                    {/* Right Panel: Symptom Form & Confirmation */}
                    <div className="clinic-card">
                      <h3 style={{ fontSize: '1.3rem', fontWeight: 600, marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <FileText size={22} color="var(--primary)" />
                        Symptom & Urgency Form
                      </h3>
                      
                      <form onSubmit={handleConfirmBooking}>
                        <div className="form-group">
                          <label className="form-label">Describe your Symptoms</label>
                          <textarea 
                            className="form-input" 
                            rows={4} 
                            placeholder="e.g. Experiencing high throat cough, dry chest congestion, fever since 2 days..."
                            value={symptoms}
                            onChange={(e) => setSymptoms(e.target.value)}
                            required
                          />
                        </div>

                        <div className="form-group" style={{ marginBottom: '2rem' }}>
                          <label className="form-label">Urgency Level</label>
                          <select 
                            className="form-input" 
                            value={urgency} 
                            onChange={(e) => setUrgency(e.target.value as 'LOW' | 'MEDIUM' | 'HIGH')}
                          >
                            <option value="LOW">Low - General Checkup</option>
                            <option value="MEDIUM">Medium - Symptom discomfort</option>
                            <option value="HIGH">High - Severe illness / High fever</option>
                          </select>
                        </div>

                        <button className="btn btn-primary" style={{ width: '100%', padding: '0.9rem' }} disabled={!holdId}>
                          Confirm & Book Appointment
                        </button>
                        {!holdId && (
                          <p style={{ fontSize: '0.8rem', color: 'var(--error)', marginTop: '0.5rem', textAlign: 'center', fontWeight: 500 }}>
                            * Secure a time slot on the left before confirming.
                          </p>
                        )}
                      </form>
                    </div>

                  </div>
                )}
              </div>
            )}
          </>
        )}

        {/* ------------------- DOCTOR PORTAL ------------------- */}
        {user.role === 'DOCTOR' && (
          <div>
            <div style={{ marginBottom: '2rem' }}>
              <h2 style={{ fontSize: '1.8rem', fontWeight: 700 }}>Daily Appointment Schedule</h2>
              <p style={{ color: 'var(--text-medium)' }}>Track schedules, review AI symptom summaries, and log clinical summaries.</p>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: '2rem', flexWrap: 'wrap' }}>
              
              {/* Left Column: Daily appointments schedule list */}
              <div>
                <h3 style={{ fontSize: '1.3rem', fontWeight: 700, marginBottom: '1rem' }}>Appointments List</h3>
                <div className="table-container">
                  <table className="clinic-table">
                    <thead>
                      <tr>
                        <th>Patient</th>
                        <th>Date / Time</th>
                        <th>Urgency</th>
                        <th>Status</th>
                        <th>Action</th>
                      </tr>
                    </thead>
                    <tbody>
                      {doctorAppointments.length === 0 ? (
                        <tr>
                          <td colSpan={5} style={{ textAlign: 'center', padding: '3rem 2rem', color: 'var(--text-light)' }}>
                            No appointments scheduled.
                          </td>
                        </tr>
                      ) : (
                        doctorAppointments.map(app => (
                          <tr key={app.appointmentId}>
                            <td style={{ fontWeight: 600 }}>{app.patientName}</td>
                            <td>
                              <div style={{ display: 'flex', flexDirection: 'column' }}>
                                <span>{app.appointmentDate}</span>
                                <span style={{ fontSize: '0.8rem', color: 'var(--text-light)' }}>{app.startTime} - {app.endTime}</span>
                              </div>
                            </td>
                            <td>
                              <span style={{ 
                                color: app.urgencyLevel === 'HIGH' ? 'var(--error)' : app.urgencyLevel === 'MEDIUM' ? 'var(--warning)' : 'var(--text-medium)',
                                fontWeight: 600
                              }}>
                                {app.urgencyLevel}
                              </span>
                            </td>
                            <td>
                              <span className={`badge ${
                                app.status === 'CONFIRMED' ? 'badge-confirmed' : app.status === 'HELD' ? 'badge-held' : 'badge-cancelled'
                              }`}>
                                {app.status.replace(/_/g, ' ')}
                              </span>
                            </td>
                            <td>
                              <button 
                                className="btn btn-outline" 
                                style={{ padding: '0.4rem 0.75rem', fontSize: '0.8rem' }}
                                onClick={() => {
                                  setSelectedAppointment(app);
                                  setClinicalNotes(app.postVisitNotes || '');
                                  setPrescriptions(app.prescriptions || []);
                                }}
                              >
                                View / Complete
                              </button>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Right Column: Complete appointment details and clinical summary form */}
              <div>
                {selectedAppointment ? (
                  <div className="clinic-card">
                    <h3 style={{ fontSize: '1.3rem', fontWeight: 600, marginBottom: '0.5rem' }}>Patient: {selectedAppointment.patientName}</h3>
                    <p style={{ color: 'var(--text-medium)', fontSize: '0.85rem', marginBottom: '1.25rem' }}>
                      Time: {selectedAppointment.startTime} - {selectedAppointment.endTime} | Urgency: {selectedAppointment.urgencyLevel}
                    </p>

                    {/* AI pre-visit summary panel */}
                    <div style={{ marginBottom: '1.5rem', borderBottom: '1px solid var(--border)', paddingBottom: '1rem' }}>
                      <h4 style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-medium)' }}>Raw Symptoms Description:</h4>
                      <p style={{ fontSize: '0.9rem', color: 'var(--text-dark)', marginTop: '0.15rem' }}>
                        {selectedAppointment.patientSymptoms || 'None described'}
                      </p>
                      {selectedAppointment.preVisitSummary && (
                        <div style={{ marginTop: '0.75rem' }}>
                          <h4 style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--primary-hover)' }}>AI Pre-Visit Symptoms Analysis:</h4>
                          <p style={{ fontSize: '0.85rem', backgroundColor: 'var(--primary-light)', color: 'var(--text-dark)', padding: '0.75rem', borderRadius: 'var(--radius-sm)', marginTop: '0.25rem', whiteSpace: 'pre-wrap' }}>
                            {selectedAppointment.preVisitSummary}
                          </p>
                        </div>
                      )}
                    </div>

                    {/* Post-visit clinical notes completion form */}
                    <form onSubmit={handleCompleteVisitSubmit}>
                      <div className="form-group">
                        <label className="form-label">Clinical Visit Notes</label>
                        <textarea 
                          className="form-input" 
                          rows={4} 
                          placeholder="Log clinical diagnosis notes here..."
                          value={clinicalNotes}
                          onChange={(e) => setClinicalNotes(e.target.value)}
                          required
                        />
                      </div>

                      {/* Prescriptions adding block */}
                      <div style={{ marginBottom: '1.5rem' }}>
                        <label className="form-label">Add Prescriptions</label>
                        
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem', marginTop: '0.25rem', marginBottom: '0.5rem' }}>
                          <input type="text" className="form-input" placeholder="Medication Name" value={medName} onChange={(e) => setMedName(e.target.value)} />
                          <input type="text" className="form-input" placeholder="Dosage (e.g. 1 tab)" value={medDosage} onChange={(e) => setMedDosage(e.target.value)} />
                        </div>
                        <div style={{ display: 'grid', gridTemplateColumns: '1.5fr 1fr', gap: '0.5rem', marginBottom: '0.75rem' }}>
                          <select className="form-input" value={medFreq} onChange={(e) => setMedFreq(e.target.value)}>
                            <option value="ONCE_DAILY">Once Daily</option>
                            <option value="TWICE_DAILY">Twice Daily</option>
                            <option value="THRICE_DAILY">Thrice Daily</option>
                          </select>
                          <input type="number" className="form-input" placeholder="Duration (days)" value={medDuration} onChange={(e) => setMedDuration(parseInt(e.target.value) || 0)} min={1} />
                        </div>
                        
                        <button type="button" className="btn btn-outline" style={{ width: '100%', padding: '0.5rem', display: 'flex', gap: '0.25rem', justifyContent: 'center' }} onClick={handleAddPrescription}>
                          <Plus size={16} />
                          Add Medication
                        </button>

                        {/* List added prescriptions */}
                        {prescriptions.length > 0 && (
                          <div style={{ marginTop: '1rem', border: '1px solid var(--border)', borderRadius: 'var(--radius-sm)', padding: '0.75rem', backgroundColor: 'var(--bg-primary)' }}>
                            <h5 style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-medium)', marginBottom: '0.5rem' }}>Added Prescription List:</h5>
                            <ul style={{ listStyle: 'none', paddingLeft: 0 }}>
                              {prescriptions.map((pres, idx) => (
                                <li key={idx} style={{ fontSize: '0.8rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem', borderBottom: '1px solid #e2e8f0', paddingBottom: '0.4rem' }}>
                                  <span>{pres.medicationName} ({pres.dosage}) - {pres.frequency.replace(/_/g, ' ')} for {pres.durationDays}d</span>
                                  <button type="button" style={{ background: 'none', border: 'none', color: 'var(--error)', cursor: 'pointer' }} onClick={() => handleRemovePrescription(idx)}>
                                    <Trash2 size={14} />
                                  </button>
                                </li>
                              ))}
                            </ul>
                          </div>
                        )}
                      </div>

                      <button className="btn btn-secondary" style={{ width: '100%', padding: '0.9rem' }}>
                        <CheckCircle size={18} />
                        Complete Visit & Generate AI Summary
                      </button>
                    </form>
                  </div>
                ) : (
                  <div className="clinic-card" style={{ textAlign: 'center', padding: '3rem 2rem' }}>
                    <FileText size={48} style={{ color: 'var(--text-light)', marginBottom: '1rem' }} />
                    <h3 style={{ fontSize: '1.2rem', fontWeight: 600 }}>Select Appointment</h3>
                    <p style={{ color: 'var(--text-medium)', fontSize: '0.9rem', marginTop: '0.25rem' }}>Choose an appointment on the left to view notes and log summaries.</p>
                  </div>
                )}
              </div>

            </div>
          </div>
        )}

        {/* ------------------- ADMIN PORTAL ------------------- */}
        {user.role === 'ADMIN' && (
          <>
            {activeTab === 'dashboard' && (
              <div>
                <div style={{ marginBottom: '2rem' }}>
                  <h2 style={{ fontSize: '1.8rem', fontWeight: 700 }}>Clinic Doctor Specialists Directory</h2>
                  <p style={{ color: 'var(--text-medium)' }}>Register, update, and manage doctor profiles.</p>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '2fr 1.2fr', gap: '2rem', flexWrap: 'wrap' }}>
                  
                  {/* Left Column: Register / Update Form */}
                  <div>
                    <h3 style={{ fontSize: '1.3rem', fontWeight: 700, marginBottom: '1rem' }}>Doctors Profiles List</h3>
                    <div className="table-container">
                      <table className="clinic-table">
                        <thead>
                          <tr>
                            <th>Specialist Name</th>
                            <th>Email</th>
                            <th>Specialization</th>
                            <th>Working Hours</th>
                            <th>Actions</th>
                          </tr>
                        </thead>
                        <tbody>
                          {adminDoctors.length === 0 ? (
                            <tr>
                              <td colSpan={5} style={{ textAlign: 'center', padding: '3rem 2rem' }}>No doctors registered.</td>
                            </tr>
                          ) : (
                            adminDoctors.map(doc => (
                              <tr key={doc.id}>
                                <td style={{ fontWeight: 600 }}>Dr. {doc.fullName}</td>
                                <td>{doc.email}</td>
                                <td>
                                  <span style={{ backgroundColor: 'var(--primary-light)', padding: '0.25rem 0.5rem', borderRadius: 'var(--radius-sm)', fontSize: '0.8rem', fontWeight: 600, color: 'var(--primary-hover)' }}>
                                    {doc.specialization}
                                  </span>
                                </td>
                                <td>{doc.workingHoursStart} - {doc.workingHoursEnd} ({doc.slotDurationMinutes} min)</td>
                                <td>
                                  <button className="btn btn-outline" style={{ padding: '0.35rem 0.6rem', fontSize: '0.75rem' }} onClick={() => setEditingDoc(doc)}>
                                    Edit Profile
                                  </button>
                                </td>
                              </tr>
                            ))
                          )}
                        </tbody>
                      </table>
                    </div>
                  </div>

                  {/* Right Column: Register Doctor Profile form */}
                  <div>
                    {editingDoc ? (
                      <div className="clinic-card">
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                          <h3 style={{ fontSize: '1.25rem', fontWeight: 600 }}>Edit Dr. {editingDoc.fullName}</h3>
                          <button className="btn" style={{ padding: '0.25rem', background: 'none', border: 'none', color: 'var(--text-medium)' }} onClick={() => setEditingDoc(null)}>✕</button>
                        </div>
                        <form onSubmit={handleUpdateDoctorSubmit}>
                          <div className="form-group">
                            <label className="form-label">Specialization</label>
                            <input type="text" className="form-input" value={editingDoc.specialization} onChange={(e) => setEditingDoc({ ...editingDoc, specialization: e.target.value })} required />
                          </div>
                          
                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem' }}>
                            <div className="form-group">
                              <label className="form-label">Shift Start</label>
                              <input type="text" className="form-input" value={editingDoc.workingHoursStart} onChange={(e) => setEditingDoc({ ...editingDoc, workingHoursStart: e.target.value })} required />
                            </div>
                            <div className="form-group">
                              <label className="form-label">Shift End</label>
                              <input type="text" className="form-input" value={editingDoc.workingHoursEnd} onChange={(e) => setEditingDoc({ ...editingDoc, workingHoursEnd: e.target.value })} required />
                            </div>
                          </div>

                          <div className="form-group" style={{ marginBottom: '1.5rem' }}>
                            <label className="form-label">Slot Duration (minutes)</label>
                            <input type="number" className="form-input" value={editingDoc.slotDurationMinutes} onChange={(e) => setEditingDoc({ ...editingDoc, slotDurationMinutes: parseInt(e.target.value) || 0 })} required />
                          </div>

                          <button className="btn btn-primary" style={{ width: '100%' }}>Update Doctor Profile</button>
                        </form>
                      </div>
                    ) : (
                      <div className="clinic-card">
                        <h3 style={{ fontSize: '1.25rem', fontWeight: 600, marginBottom: '1.5rem' }}>Register Doctor Profile</h3>
                        <form onSubmit={handleRegisterDoctorSubmit}>
                          <div className="form-group">
                            <label className="form-label">Full Name</label>
                            <input type="text" className="form-input" placeholder="e.g. John Watson" value={newDocName} onChange={(e) => setNewDocName(e.target.value)} required />
                          </div>

                          <div className="form-group">
                            <label className="form-label">Email address</label>
                            <input type="email" className="form-input" placeholder="watson@healthcare.com" value={newDocEmail} onChange={(e) => setNewDocEmail(e.target.value)} required />
                          </div>

                          <div className="form-group">
                            <label className="form-label">Password</label>
                            <input type="password" className="form-input" placeholder="••••••••" value={newDocPassword} onChange={(e) => setNewDocPassword(e.target.value)} required />
                          </div>

                          <div className="form-group">
                            <label className="form-label">Specialization</label>
                            <input type="text" className="form-input" placeholder="Cardiology, General, Surgery..." value={newDocSpec} onChange={(e) => setNewDocSpec(e.target.value)} required />
                          </div>

                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem' }}>
                            <div className="form-group">
                              <label className="form-label">Shift Start</label>
                              <input type="text" className="form-input" value={newDocHoursStart} onChange={(e) => setNewDocHoursStart(e.target.value)} required />
                            </div>
                            <div className="form-group">
                              <label className="form-label">Shift End</label>
                              <input type="text" className="form-input" value={newDocHoursEnd} onChange={(e) => setNewDocHoursEnd(e.target.value)} required />
                            </div>
                          </div>

                          <div className="form-group" style={{ marginBottom: '1.5rem' }}>
                            <label className="form-label">Slot Duration (minutes)</label>
                            <input type="number" className="form-input" value={newDocDuration} onChange={(e) => setNewDocDuration(parseInt(e.target.value) || 0)} required min={15} />
                          </div>

                          <button className="btn btn-primary" style={{ width: '100%' }}>Register Doctor Account</button>
                        </form>
                      </div>
                    )}
                  </div>

                </div>
              </div>
            )}

            {activeTab === 'leaves' && (
              <div>
                <div style={{ marginBottom: '2rem' }}>
                  <h2 style={{ fontSize: '1.8rem', fontWeight: 700 }}>Manage Doctor Leaves</h2>
                  <p style={{ color: 'var(--text-medium)' }}>Schedule leaves and trigger automatic conflict resolutions for booked slots.</p>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1.2fr 2fr', gap: '2rem', flexWrap: 'wrap' }}>
                  
                  {/* Left Column: Form to schedule leave */}
                  <div className="clinic-card">
                    <h3 style={{ fontSize: '1.25rem', fontWeight: 600, marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <CalendarDays size={22} color="var(--primary)" />
                      Schedule Doctor Leave
                    </h3>
                    <form onSubmit={handleAddLeaveSubmit}>
                      <div className="form-group">
                        <label className="form-label">Select Doctor Specialist</label>
                        <select className="form-input" value={leaveDocId} onChange={(e) => { setLeaveDocId(e.target.value); if(e.target.value) handleFetchLeaves(parseInt(e.target.value)); }} required>
                          <option value="">-- Choose Doctor Profile --</option>
                          {adminDoctors.map(doc => (
                            <option key={doc.id} value={doc.id}>Dr. {doc.fullName} ({doc.specialization})</option>
                          ))}
                        </select>
                      </div>

                      <div className="form-group">
                        <label className="form-label">Leave Date</label>
                        <input type="date" className="form-input" value={leaveDate} onChange={(e) => setLeaveDate(e.target.value)} required min={new Date().toISOString().split('T')[0]} />
                      </div>

                      <div className="form-group" style={{ marginBottom: '1.5rem' }}>
                        <label className="form-label">Reason for Leave</label>
                        <input type="text" className="form-input" placeholder="e.g. Medical conference, Sick leave" value={leaveReason} onChange={(e) => setLeaveReason(e.target.value)} required />
                      </div>

                      <button className="btn btn-primary" style={{ width: '100%', padding: '0.85rem' }}>
                        Submit Leave & Resolve Conflicts
                      </button>
                    </form>
                  </div>

                  {/* Right Column: Selected doctor leaves schedule list */}
                  <div>
                    <h3 style={{ fontSize: '1.3rem', fontWeight: 700, marginBottom: '1rem' }}>Leaves Registry Log</h3>
                    <div className="table-container">
                      <table className="clinic-table">
                        <thead>
                          <tr>
                            <th>Leave Date</th>
                            <th>Reason</th>
                            <th>Action</th>
                          </tr>
                        </thead>
                        <tbody>
                          {!leaveDocId ? (
                            <tr>
                              <td colSpan={3} style={{ textAlign: 'center', padding: '3rem 2rem', color: 'var(--text-light)' }}>
                                Choose a doctor on the left to view active leave dates.
                              </td>
                            </tr>
                          ) : doctorLeaves.length === 0 ? (
                            <tr>
                              <td colSpan={3} style={{ textAlign: 'center', padding: '3rem 2rem', color: 'var(--text-light)' }}>
                                No leaves registered for this doctor profile.
                              </td>
                            </tr>
                          ) : (
                            doctorLeaves.map(leave => (
                              <tr key={leave.id}>
                                <td style={{ fontWeight: 600 }}>{leave.leaveDate}</td>
                                <td>{leave.reason}</td>
                                <td>
                                  <button className="btn btn-outline" style={{ padding: '0.35rem 0.6rem', color: 'var(--error)', border: 'none', fontSize: '0.75rem' }} onClick={() => handleDeleteLeave(parseInt(leaveDocId as string), leave.id)}>
                                    Delete Leave
                                  </button>
                                </td>
                              </tr>
                            ))
                          )}
                        </tbody>
                      </table>
                    </div>
                  </div>

                </div>
              </div>
            )}

            {activeTab === 'conflicts' && (
              <div>
                <div style={{ marginBottom: '1.5rem' }}>
                  <h2 style={{ fontSize: '1.8rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                    <AlertTriangle size={32} color="var(--warning)" />
                    Leave Conflict Resolutions List
                  </h2>
                  <p style={{ color: 'var(--text-medium)' }}>System-audited appointments that were automatically cancelled when doctors scheduled leaves.</p>
                </div>

                <div className="table-container">
                  <table className="clinic-table">
                    <thead>
                      <tr>
                        <th>Affected Patient</th>
                        <th>Doctor Specialist</th>
                        <th>Conflicting Time Slot</th>
                        <th>Cancellation Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {conflicts.length === 0 ? (
                        <tr>
                          <td colSpan={4} style={{ textAlign: 'center', padding: '4rem 2rem', color: 'var(--text-light)', fontSize: '0.95rem' }}>
                            No conflicts found. All calendar schedules are clean.
                          </td>
                        </tr>
                      ) : (
                        conflicts.map(c => (
                          <tr key={c.appointmentId}>
                            <td style={{ fontWeight: 600 }}>
                              <div style={{ display: 'flex', flexDirection: 'column' }}>
                                <span>{c.patientName}</span>
                                <span style={{ fontSize: '0.75rem', color: 'var(--text-light)' }}>{c.patientEmail}</span>
                              </div>
                            </td>
                            <td>Dr. {c.doctorName} ({c.doctorSpecialization})</td>
                            <td>
                              <span style={{ fontWeight: 500 }}>{c.appointmentDate}</span>
                              <span style={{ color: 'var(--text-light)', fontSize: '0.85rem', marginLeft: '0.5rem' }}>({c.startTime} - {c.endTime})</span>
                            </td>
                            <td>
                              <span className="badge badge-cancelled">
                                {c.status.replace(/_/g, ' ')}
                              </span>
                            </td>
                          </tr>
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </>
        )}

      </main>

      {/* Footer */}
      <footer className="footer">
        <p>© 2026 CareFlow Health Inc. Multi-Portal Clinical Management Dashboard.</p>
      </footer>
    </div>
  );
}

export default App;
