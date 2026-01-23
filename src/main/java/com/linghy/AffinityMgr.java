package com.linghy;

import com.sun.jna.*;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;

public class AffinityMgr
{
    public static void init()
    {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win"))
        {
            setWindowsPOnly();
        }
        else if (os.contains("linux") || os.contains("nix") || os.contains("nux"))
        {
            setLinuxPOnly();
        }
        else
        {
            System.out.println("p-cores not sup on " + os);
        }
    }

    private static void setWindowsPOnly()
    {
        try {
            Kernel32 kernel = Kernel32.INSTANCE;

            WinNT.HANDLE process = kernel.GetCurrentProcess();

            long maskValue = 0xFFFFL;
            BaseTSD.ULONG_PTR affinityMask = new BaseTSD.ULONG_PTR(maskValue);

            boolean success = kernel.SetProcessAffinityMask(process, affinityMask);

            if (success)
            {
                System.out.println("sched_setaffinity success: 0x" + Long.toHexString(maskValue));
            }
            else
            {
                int errorCode = kernel.GetLastError();
                System.err.println("err SetProcessAffinityMask: " + errorCode);
            }
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e)
        {
            System.err.println("jna not found");
        } catch (Exception e) {
            System.err.println("affinity err: " + e);
        }
    }

    private static void setLinuxPOnly()
    {
        try {
            LibC libc = Native.load("c", LibC.class);

            int CPU_SETSIZE_BYTES = 128;
            Memory mask = new Memory(CPU_SETSIZE_BYTES);
            mask.clear();

            mask.setByte(0, (byte) 0xFF);
            mask.setByte(1, (byte) 0xFF);

            int rc = libc.sched_setaffinity(0, new NativeLong(CPU_SETSIZE_BYTES), mask);

            if (rc == 0)
            {
                System.out.println("sched_setaffinity success");
            }
            else
            {
                System.err.println("sched_setaffinity err: " + (-rc) + " / " + Native.getLastError());
            }
        } catch (Exception e)
        {
            System.err.println("JNA err: " + e);
        }
    }

    public interface LibC extends Library {
        int sched_setaffinity(int pid, NativeLong cpusetsize, Pointer mask);
    }
}